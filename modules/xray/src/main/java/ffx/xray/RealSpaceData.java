/**
 * Title: Force Field X.
 *
 * Description: Force Field X - Software for Molecular Biophysics.
 *
 * Copyright: Copyright (c) Michael J. Schnieders 2001-2016.
 *
 * This file is part of Force Field X.
 *
 * Force Field X is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * Force Field X is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Force Field X; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Linking this library statically or dynamically with other modules is making a
 * combined work based on this library. Thus, the terms and conditions of the
 * GNU General Public License cover the whole combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent modules, and
 * to copy and distribute the resulting executable under terms of your choice,
 * provided that you also meet, for each linked independent module, the terms
 * and conditions of the license of that module. An independent module is a
 * module which is not derived from or based on this library. If you modify this
 * library, you may extend this exception to your version of the library, but
 * you are not obligated to do so. If you do not wish to do so, delete this
 * exception statement from your version.
 */
package ffx.xray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

import org.apache.commons.configuration.CompositeConfiguration;

import static org.apache.commons.math3.util.FastMath.floor;

import edu.rit.pj.IntegerForLoop;
import edu.rit.pj.ParallelRegion;
import edu.rit.pj.ParallelTeam;
import edu.rit.pj.reduction.SharedDouble;

import ffx.crystal.Crystal;
import ffx.numerics.AtomicDoubleArray;
import ffx.numerics.MultiDoubleArray;
import ffx.numerics.TriCubicSpline;
import ffx.potential.MolecularAssembly;
import ffx.potential.bonded.Atom;
import ffx.potential.bonded.Molecule;
import ffx.potential.bonded.Residue;
import ffx.xray.RefinementMinimize.RefinementMode;

import static ffx.crystal.Crystal.mod;

/**
 * <p>
 * RealSpaceData class.</p>
 *
 * @author Timothy D. Fenn
 *
 */
public class RealSpaceData implements DataContainer {

    private static final Logger logger = Logger.getLogger(RealSpaceData.class.getName());

    /**
     * The collection of MolecularAssembly instances that model the real space
     * data.
     */
    protected final MolecularAssembly[] molecularAssemblies;
    /**
     * The real space data files.
     */
    protected final RealSpaceFile[] realSpaceFile;
    /**
     * The name of the model.
     */
    protected final String modelName;
    /**
     * The number of real space data sources to consider.
     */
    protected final int nRealSpaceData;
    /**
     * The collection of crystals that describe the PBC and symmetry of each
     * data source.
     */
    protected final Crystal crystal[];
    /**
     * The real space refinement data for each data source.
     */
    protected final RealSpaceRefinementData[] refinementData;
    /**
     * The refinement model.
     */
    protected final RefinementModel refinementModel;
    /**
     * The total real space energy.
     */
    private double realSpaceEnergy = 0.0;
    /**
     * The partial derivative of the real space energy with respect to lambda.
     */
    private double realSpacedUdL = 0.0;
    /**
     * The real space gradient.
     */
    private double realSpaceGradient[];
    /**
     * The partial derivative of the real space gradient with respect to lambda.
     */
    private double realSpacedUdXdL[];
    /**
     * The weight of the data relative to the weight of the force field.
     */
    public double xweight;
    /**
     * The current value of the state variable lambda.
     */
    protected double lambda = 1.0;
    /**
     * A flag to indicate if the lambda term is active.
     */
    private boolean lambdaTerm = false;

    private final boolean parallel = true;
    private final ParallelTeam parallelTeam;
    private final RealSpaceRegion realSpaceRegion;

    /**
     * Construct a real space data molecularAssemblies, assumes a real space map
     * with a weight of 1.0 using the same name as the molecular
     * molecularAssemblies.
     *
     * @param molecularAssembly
     * {@link ffx.potential.MolecularAssembly molecular molecularAssemblies}
     * object, used as the atomic model for comparison against the data
     * @param properties system properties file
     * @param parallelTeam
     * @param diffractionData {@link ffx.xray.DiffractionData diffraction data}
     */
    public RealSpaceData(MolecularAssembly molecularAssembly,
            CompositeConfiguration properties,
            ParallelTeam parallelTeam,
            DiffractionData diffractionData) {
        this(new MolecularAssembly[]{molecularAssembly}, properties, parallelTeam, diffractionData);
    }

    /**
     * Construct a real space data molecularAssemblies, assumes a real space map
     * with a weight of 1.0 using the same name as the molecularAssemblies.
     *
     * @param molecularAssemblies
     * {@link ffx.potential.MolecularAssembly molecular molecularAssemblies}
     * object, used as the atomic model for comparison against the data
     * @param properties system properties file
     * @param parallelTeam
     * @param diffractionData {@link ffx.xray.DiffractionData diffraction data}
     */
    public RealSpaceData(MolecularAssembly molecularAssemblies[],
            CompositeConfiguration properties,
            ParallelTeam parallelTeam,
            DiffractionData diffractionData) {
        this.molecularAssemblies = molecularAssemblies;
        this.parallelTeam = parallelTeam;
        this.modelName = molecularAssemblies[0].getFile().getName();
        this.realSpaceFile = null;
        this.nRealSpaceData = 1;
        crystal = new Crystal[nRealSpaceData];
        crystal[0] = diffractionData.crystal[0];
        refinementData = new RealSpaceRefinementData[nRealSpaceData];
        refinementData[0] = new RealSpaceRefinementData();
        refinementData[0].periodic = true;

        xweight = properties.getDouble("xweight", 1.0);
        lambdaTerm = properties.getBoolean("lambdaterm", false);

        if (logger.isLoggable(Level.INFO)) {
            StringBuilder sb = new StringBuilder();
            sb.append(" Real Space Refinement Settings\n");
            sb.append(format("  Refinement weight (xweight): %5.3f", xweight));
            logger.info(sb.toString());
        }

        if (!diffractionData.scaled[0]) {
            diffractionData.scaleBulkFit();
            diffractionData.printStats();
        }

        // get Fo-Fc difference density
        diffractionData.crs_fc[0].computeAtomicGradients(diffractionData.refinementData[0].fofc1,
                diffractionData.refinementData[0].freer,
                diffractionData.refinementData[0].rfreeflag,
                RefinementMode.COORDINATES);

        refinementData[0].ori[0] = 0;
        refinementData[0].ori[1] = 0;
        refinementData[0].ori[2] = 0;
        int extx = (int) diffractionData.crs_fc[0].getXDim();
        int exty = (int) diffractionData.crs_fc[0].getYDim();
        int extz = (int) diffractionData.crs_fc[0].getZDim();
        refinementData[0].ext[0] = extx;
        refinementData[0].ext[1] = exty;
        refinementData[0].ext[2] = extz;
        refinementData[0].ni[0] = extx;
        refinementData[0].ni[1] = exty;
        refinementData[0].ni[2] = extz;
        refinementData[0].data = new double[extx * exty * extz];
        for (int k = 0; k < extz; k++) {
            for (int j = 0; j < exty; j++) {
                for (int i = 0; i < extx; i++) {
                    int index1 = (i + extx * (j + exty * k));
                    int index2 = 2 * (i + extx * (j + exty * k));
                    refinementData[0].data[index1] = diffractionData.crs_fc[0].densityGrid[index2];
                }
            }
        }

        /**
         * Initialize the refinement model.
         */
        refinementModel = new RefinementModel(molecularAssemblies);

        /**
         * Initialize the RealSpaceRegion.
         */
        int nAtoms = refinementModel.totalAtomArray.length;
        realSpaceRegion = new RealSpaceRegion(parallelTeam.getThreadCount(),
                nAtoms, refinementData.length);

    }

    /**
     * Construct a real space data molecularAssemblies, assumes a real space map
     * with a weight of 1.0 using the same name as the molecular
     * molecularAssemblies.
     *
     * @param assembly
     * {@link ffx.potential.MolecularAssembly molecular molecularAssemblies}
     * object, used as the atomic model for comparison against the data
     * @param properties system properties file
     * @param parallelTeam
     */
    public RealSpaceData(MolecularAssembly assembly,
            CompositeConfiguration properties,
            ParallelTeam parallelTeam) {
        this(new MolecularAssembly[]{assembly}, properties,
                parallelTeam, new RealSpaceFile(assembly));
    }

    /**
     * Construct a real space data molecularAssemblies.
     *
     * @param assembly
     * {@link ffx.potential.MolecularAssembly molecular molecularAssemblies}
     * object, used as the atomic model for comparison against the data
     * @param properties system properties file
     * @param parallelTeam
     * @param datafile one or more {@link RealSpaceFile} to be refined against
     */
    public RealSpaceData(MolecularAssembly assembly,
            CompositeConfiguration properties,
            ParallelTeam parallelTeam,
            RealSpaceFile... datafile) {
        this(new MolecularAssembly[]{assembly}, properties, parallelTeam, datafile);
    }

    /**
     * Construct a real space data molecularAssemblies.
     *
     * @param molecularAssemblies
     * {@link ffx.potential.MolecularAssembly molecular molecularAssemblies}
     * object array (typically containing alternate conformer assemblies), used
     * as the atomic model for comparison against the data
     * @param properties system properties file
     * @param parallelTeam
     * @param dataFile one or more {@link RealSpaceFile} to be refined against
     */
    public RealSpaceData(MolecularAssembly molecularAssemblies[],
            CompositeConfiguration properties,
            ParallelTeam parallelTeam,
            RealSpaceFile... dataFile) {

        this.molecularAssemblies = molecularAssemblies;
        this.modelName = molecularAssemblies[0].getFile().getName();
        this.parallelTeam = parallelTeam;
        this.realSpaceFile = dataFile;
        this.nRealSpaceData = dataFile.length;
        crystal = new Crystal[nRealSpaceData];
        refinementData = new RealSpaceRefinementData[nRealSpaceData];

        xweight = properties.getDouble("xweight", 1.0);
        lambdaTerm = properties.getBoolean("lambdaterm", false);

        for (int i = 0; i < nRealSpaceData; i++) {
            crystal[i] = dataFile[i].realSpaceFileFilter.getCrystal(dataFile[i].filename, properties);

            if (crystal[i] == null) {
                logger.severe("CCP4 map file does not contain full crystal information!");
            }
        }

        for (int i = 0; i < nRealSpaceData; i++) {
            refinementData[i] = new RealSpaceRefinementData();
            dataFile[i].realSpaceFileFilter.readFile(dataFile[i].filename,
                    refinementData[i], properties);

            if (refinementData[i].ori[0] == 0
                    && refinementData[i].ori[1] == 0
                    && refinementData[i].ori[2] == 0
                    && refinementData[i].ext[0] == refinementData[i].ni[0]
                    && refinementData[i].ext[1] == refinementData[i].ni[1]
                    && refinementData[i].ext[2] == refinementData[i].ni[2]) {
                refinementData[i].periodic = true;
            }
        }

        if (logger.isLoggable(Level.INFO)) {
            StringBuilder sb = new StringBuilder();
            sb.append(" Real Space Refinement Settings\n");
            sb.append(format("  Refinement weight (xweight): %5.3f", xweight));
            logger.info(sb.toString());
        }

        // now set up the refinement model
        refinementModel = new RefinementModel(molecularAssemblies);

        /**
         * Initialize the RealSpaceRegion.
         */
        int nAtoms = refinementModel.totalAtomArray.length;
        realSpaceRegion = new RealSpaceRegion(parallelTeam.getThreadCount(),
                nAtoms, refinementData.length);

    }

    /**
     * compute real space target value, also fills in atomic derivatives
     *
     * @return target value sum over all data sets.
     */
    public double computeRealSpaceTarget() {

        long time = -System.nanoTime();
        // Zero out the realSpaceTarget energy.
        realSpaceEnergy = 0.0;
        // Zero out the realSpacedUdL energy.
        realSpacedUdL = 0.0;
        // Initialize gradient to zero; allocate space if necessary.
        int nActive = 0;
        int nAtoms = refinementModel.totalAtomArray.length;
        for (int i = 0; i < nAtoms; i++) {
            if (refinementModel.totalAtomArray[i].isActive()) {
                nActive++;
            }
        }

        int nGrad = nActive * 3;
        if (realSpaceGradient == null || realSpaceGradient.length < nGrad) {
            realSpaceGradient = new double[nGrad];
        } else {
            Arrays.fill(realSpaceGradient, 0.0);
        }

        // Initialize dUdXdL to zero; allocate space if necessary.
        if (realSpacedUdXdL == null || realSpacedUdXdL.length < nGrad) {
            realSpacedUdXdL = new double[nGrad];
        } else {
            Arrays.fill(realSpacedUdXdL, 0.0);
        }

        if (parallel) {
            try {
                parallelTeam.execute(realSpaceRegion);
            } catch (Exception e) {
                String message = " Exception computing real space energy";
                logger.log(Level.SEVERE, message, e);
            }
        }

        time += System.nanoTime();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(format(" Real space energy time: %16.8f (sec).", time * 1.0e-9));
        }

        return realSpaceEnergy;
    }

    public double getRealSpaceEnergy() {
        return realSpaceEnergy;
    }

    public double getdEdL() {
        return realSpacedUdL;
    }

    public double[] getRealSpaceGradient(double gradient[]) {
        int nAtoms = refinementModel.totalAtomArray.length;
        int nActiveAtoms = 0;
        for (int i = 0; i < nAtoms; i++) {
            if (refinementModel.totalAtomArray[i].isActive()) {
                nActiveAtoms++;
            }
        }

        if (gradient == null || gradient.length < nActiveAtoms * 3) {
            gradient = new double[nActiveAtoms * 3];
        }
        for (int i = 0; i < nActiveAtoms * 3; i++) {
            gradient[i] += realSpaceGradient[i];
        }
        return gradient;
    }

    public double[] getdEdXdL(double gradient[]) {
        int nAtoms = refinementModel.totalAtomArray.length;
        int nActiveAtoms = 0;
        for (int i = 0; i < nAtoms; i++) {
            if (refinementModel.totalAtomArray[i].isActive()) {
                nActiveAtoms++;
            }
        }

        if (gradient == null || gradient.length < nActiveAtoms * 3) {
            gradient = new double[nActiveAtoms * 3];
        }

        for (int i = 0; i < nActiveAtoms * 3; i++) {
            gradient[i] += realSpacedUdXdL[i];
        }
        return gradient;
    }

    /**
     * Set the current value of the state variable.
     *
     * @param lambda a double.
     */
    protected void setLambda(double lambda) {
        this.lambda = lambda;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Atom[] getAtomArray() {
        return refinementModel.totalAtomArray;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Atom[] getActiveAtomArray() {
        return refinementModel.activeAtomArray;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<ArrayList<Residue>> getAltResidues() {
        return refinementModel.altResidues;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ArrayList<ArrayList<Molecule>> getAltMolecules() {
        return refinementModel.altMolecules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MolecularAssembly[] getMolecularAssemblies() {
        return molecularAssemblies;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RefinementModel getRefinementModel() {
        return refinementModel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getWeight() {
        return xweight;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWeight(double weight) {
        this.xweight = weight;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String printOptimizationHeader() {
        return "Density score";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String printOptimizationUpdate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nRealSpaceData; i++) {
            sb.append(String.format("%6.2f ", refinementData[i].densityScore));
        }
        return sb.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String printEnergyUpdate() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nRealSpaceData; i++) {
            sb.append(String.format("     dataset %d (weight: %5.1f): chemical energy: %8.2f density score: %8.2f\n",
                    i + 1,
                    realSpaceFile[i].weight,
                    molecularAssemblies[0].getPotentialEnergy().getTotalEnergy(),
                    realSpaceFile[i].weight * refinementData[i].densityScore));
        }
        return sb.toString();
    }

    private class RealSpaceRegion extends ParallelRegion {

        private int nThreads;
        private int nAtoms;
        // private int nGrad;
        private int nData;
        private final AtomicDoubleArray gradX;
        private final AtomicDoubleArray gradY;
        private final AtomicDoubleArray gradZ;
        private final AtomicDoubleArray lambdaGradX;
        private final AtomicDoubleArray lambdaGradY;
        private final AtomicDoubleArray lambdaGradZ;
        private final InitializationLoop initializationLoops[];
        private final RealSpaceLoop realSpaceLoops[];
        private final SharedDouble sharedTarget[];
        private final SharedDouble shareddUdL;

        public RealSpaceRegion(int nThreads, int nAtoms, /*int nGrad, */ int nData) {
            this.nThreads = nThreads;
            this.nAtoms = nAtoms;
            //this.nGrad = nGrad;
            this.nData = nData;
            initializationLoops = new InitializationLoop[nThreads];
            realSpaceLoops = new RealSpaceLoop[nThreads];
            sharedTarget = new SharedDouble[nData];
            for (int i = 0; i < nData; i++) {
                sharedTarget[i] = new SharedDouble();
            }
            shareddUdL = new SharedDouble();
            gradX = new MultiDoubleArray(nThreads, nAtoms);
            gradY = new MultiDoubleArray(nThreads, nAtoms);
            gradZ = new MultiDoubleArray(nThreads, nAtoms);
            lambdaGradX = new MultiDoubleArray(nThreads, nAtoms);
            lambdaGradY = new MultiDoubleArray(nThreads, nAtoms);
            lambdaGradZ = new MultiDoubleArray(nThreads, nAtoms);
        }

        @Override
        public void start() {
            for (int i = 0; i < nData; i++) {
                sharedTarget[i].set(0.0);
            }
            shareddUdL.set(0.0);
            gradX.alloc(nAtoms);
            gradY.alloc(nAtoms);
            gradZ.alloc(nAtoms);
            lambdaGradX.alloc(nAtoms);
            lambdaGradY.alloc(nAtoms);
            lambdaGradZ.alloc(nAtoms);
        }

        @Override
        public void finish() {
            // Load final values.
            realSpaceEnergy = 0;
            for (int i = 0; i < nData; i++) {
                refinementData[i].densityScore = sharedTarget[i].get();
                realSpaceEnergy += refinementData[i].densityScore;
            }
            realSpacedUdL = shareddUdL.get();
            int index = 0;
            for (int i = 0; i < nAtoms; i++) {
                Atom atom = refinementModel.totalAtomArray[i];
                if (atom.isActive()) {
                    int ii = index * 3;
                    double gx = gradX.get(i);
                    double gy = gradY.get(i);
                    double gz = gradZ.get(i);
                    realSpaceGradient[ii] = gx;
                    realSpaceGradient[ii + 1] = gy;
                    realSpaceGradient[ii + 2] = gz;
                    atom.setXYZGradient(gx, gy, gz);
                    gx = lambdaGradX.get(i);
                    gy = lambdaGradY.get(i);
                    gz = lambdaGradZ.get(i);
                    realSpacedUdXdL[ii] = gx;
                    realSpacedUdXdL[ii + 1] = gy;
                    realSpacedUdXdL[ii + 2] = gz;
                    atom.setLambdaXYZGradient(gx, gy, gz);
                    index++;
                }
            }
        }

        @Override
        public void run() throws Exception {
            int threadID = getThreadIndex();

            if (initializationLoops[threadID] == null) {
                initializationLoops[threadID] = new InitializationLoop();
            }
            execute(0, nAtoms - 1, initializationLoops[threadID]);

            if (realSpaceLoops[threadID] == null) {
                realSpaceLoops[threadID] = new RealSpaceLoop();
            }
            execute(0, nAtoms - 1, realSpaceLoops[threadID]);

        }

        /**
         * Initialize gradient and lambda gradient arrays.
         */
        private class InitializationLoop extends IntegerForLoop {

            @Override
            public void run(int lb, int ub) {
                int threadID = getThreadIndex();
                gradX.reset(threadID, lb, ub);
                gradY.reset(threadID, lb, ub);
                gradZ.reset(threadID, lb, ub);
                lambdaGradX.reset(threadID, lb, ub);
                lambdaGradY.reset(threadID, lb, ub);
                lambdaGradZ.reset(threadID, lb, ub);

                for (int i=lb; i<=ub; i++) {
                    Atom a = refinementModel.totalAtomArray[i];
                    a.setXYZGradient(0.0, 0.0, 0.0);
                    a.setLambdaXYZGradient(0.0, 0.0, 0.0);
                }
            }
        }

        private class RealSpaceLoop extends IntegerForLoop {

            double target[] = new double[nData];
            double localdUdL;

            @Override
            public void start() {
                for (int i = 0; i < nData; i++) {
                    target[i] = 0;
                }
                localdUdL = 0;
            }

            @Override
            public void finish() {
                for (int i = 0; i < nData; i++) {
                    sharedTarget[i].addAndGet(target[i]);
                }
                shareddUdL.addAndGet(localdUdL);
            }

            @Override
            public void run(int first, int last) throws Exception {

                int threadID = getThreadIndex();
                double xyz[] = new double[3];
                double uvw[] = new double[3];
                double grad[] = new double[3];
                double scalar[][][] = new double[4][4][4];
                TriCubicSpline spline = new TriCubicSpline();

                for (int i = 0; i < nRealSpaceData; i++) {

                    // Define the extent of this real space data sources.
                    int extX = refinementData[i].ext[0];
                    int extY = refinementData[i].ext[1];
                    int extZ = refinementData[i].ext[2];
                    int nX = refinementData[i].ni[0];
                    int nY = refinementData[i].ni[1];
                    int nZ = refinementData[i].ni[2];
                    int originX = refinementData[i].ori[0];
                    int originY = refinementData[i].ori[1];
                    int originZ = refinementData[i].ori[2];

                    for (int ia = first; ia <= last; ia++) {
                        Atom a = refinementModel.totalAtomArray[ia];
                        /**
                         * Only include atoms in the target function that have
                         * their use flag set to true and are Active.
                         */
                        if (!a.getUse()) {
                            continue;
                        }

                        double lambdai = 1.0;
                        double dUdL = 0.0;
                        if (lambdaTerm && a.applyLambda()) {
                            lambdai = lambda;
                            dUdL = 1.0;
                        }
                        a.getXYZ(xyz);
                        crystal[i].toFractionalCoordinates(xyz, uvw);

                        /**
                         * Logic to find atom in 3d scalar field box.
                         */
                        final double frx = nX * uvw[0];
                        final int ifrx = ((int) floor(frx)) - originX;
                        final double dfrx = frx - floor(frx);

                        final double fry = nY * uvw[1];
                        final int ifry = ((int) floor(fry)) - originY;
                        final double dfry = fry - floor(fry);

                        final double frz = nZ * uvw[2];
                        final int ifrz = ((int) floor(frz)) - originZ;
                        final double dfrz = frz - floor(frz);

                        if (!refinementData[i].periodic) {
                            if (ifrx - 1 < 0 || ifrx + 2 > extX
                                    || ifry - 1 < 0 || ifry + 2 > extY
                                    || ifrz - 1 < 0 || ifrz + 2 > extZ) {
                                String message = format(" Atom %s is outside the density will be ignored.", a.toString());
                                logger.warning(message);
                                continue;
                            }
                        }

                        /**
                         * Fill in scalar 4x4 array for interpolation.
                         */
                        if (refinementData[i].periodic) {
                            for (int ui = ifrx - 1; ui < ifrx + 3; ui++) {
                                int uii = ui - (ifrx - 1);
                                int pui = mod(ui, extX);
                                for (int vi = ifry - 1; vi < ifry + 3; vi++) {
                                    int vii = vi - (ifry - 1);
                                    int pvi = mod(vi, extY);
                                    for (int wi = ifrz - 1; wi < ifrz + 3; wi++) {
                                        int wii = wi - (ifrz - 1);
                                        int pwi = mod(wi, extZ);
                                        scalar[uii][vii][wii] = refinementData[i].getDataIndex(pui, pvi, pwi);
                                    }
                                }
                            }
                        } else {
                            for (int ui = ifrx - 1; ui < ifrx + 3; ui++) {
                                int uii = ui - (ifrx - 1);
                                for (int vi = ifry - 1; vi < ifry + 3; vi++) {
                                    int vii = vi - (ifry - 1);
                                    for (int wi = ifrz - 1; wi < ifrz + 3; wi++) {
                                        int wii = wi - (ifrz - 1);
                                        scalar[uii][vii][wii] = refinementData[i].getDataIndex(ui, vi, wi);
                                    }
                                }
                            }
                        }

                        /**
                         * Scale and interpolate.
                         */
                        double scale;
                        double scaledUdL;
                        double atomicWeight = a.getAtomType().atomicWeight;
                        if (realSpaceFile != null) {
                            atomicWeight *= realSpaceFile[i].weight;
                        }
                        scale = -1.0 * lambdai * atomicWeight;
                        scaledUdL = -1.0 * dUdL * atomicWeight;

                        double val = spline.spline(dfrx, dfry, dfrz, scalar, grad);
                        target[i] += scale * val;
                        localdUdL += scaledUdL * val;

                        if (a.isActive()) {
                            grad[0] = grad[0] * nX;
                            grad[1] = grad[1] * nY;
                            grad[2] = grad[2] * nZ;
                            // transpose of toFractional
                            xyz[0] = grad[0] * crystal[i].A00 + grad[1] * crystal[i].A01 + grad[2] * crystal[i].A02;
                            xyz[1] = grad[0] * crystal[i].A10 + grad[1] * crystal[i].A11 + grad[2] * crystal[i].A12;
                            xyz[2] = grad[0] * crystal[i].A20 + grad[1] * crystal[i].A21 + grad[2] * crystal[i].A22;
                            gradX.add(threadID, ia, scale * xyz[0]);
                            gradY.add(threadID, ia, scale * xyz[1]);
                            gradZ.add(threadID, ia, scale * xyz[2]);
                            lambdaGradX.add(threadID, ia, scaledUdL * xyz[0]);
                            lambdaGradY.add(threadID, ia, scaledUdL * xyz[1]);
                            lambdaGradZ.add(threadID, ia, scaledUdL * xyz[2]);
                        }
                    }
                }
                gradX.reduce(first, last);
                gradY.reduce(first, last);
                gradZ.reduce(first, last);
                lambdaGradX.reduce(first, last);
                lambdaGradY.reduce(first, last);
                lambdaGradZ.reduce(first, last);
            }

        }

    }

}
