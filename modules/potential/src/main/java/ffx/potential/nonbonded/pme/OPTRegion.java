//******************************************************************************
//
// Title:       Force Field X.
// Description: Force Field X - Software for Molecular Biophysics.
// Copyright:   Copyright (c) Michael J. Schnieders 2001-2019.
//
// This file is part of Force Field X.
//
// Force Field X is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License version 3 as published by
// the Free Software Foundation.
//
// Force Field X is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
// details.
//
// You should have received a copy of the GNU General Public License along with
// Force Field X; if not, write to the Free Software Foundation, Inc., 59 Temple
// Place, Suite 330, Boston, MA 02111-1307 USA
//
// Linking this library statically or dynamically with other modules is making a
// combined work based on this library. Thus, the terms and conditions of the
// GNU General Public License cover the whole combination.
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules, and
// to copy and distribute the resulting executable under terms of your choice,
// provided that you also meet, for each linked independent module, the terms
// and conditions of the license of that module. An independent module is a
// module which is not derived from or based on this library. If you modify this
// library, you may extend this exception to your version of the library, but
// you are not obligated to do so. If you do not wish to do so, delete this
// exception statement from your version.
//
//******************************************************************************
package ffx.potential.nonbonded.pme;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.rit.pj.IntegerForLoop;
import edu.rit.pj.IntegerSchedule;
import edu.rit.pj.ParallelRegion;
import edu.rit.pj.reduction.SharedDoubleArray;

import ffx.potential.bonded.Atom;
import ffx.potential.nonbonded.GeneralizedKirkwood;
import ffx.potential.nonbonded.ParticleMeshEwaldCart;
import static ffx.potential.parameters.MultipoleType.t001;
import static ffx.potential.parameters.MultipoleType.t010;
import static ffx.potential.parameters.MultipoleType.t100;

public class OPTRegion extends ParallelRegion {

    private static final Logger logger = Logger.getLogger(OPTRegion.class.getName());

    /**
     * An ordered array of atoms in the system.
     */
    private Atom[] atoms;
    private double[] polarizability;
    /**
     * Dimensions of [nsymm][nAtoms][3]
     */
    public double[][][] inducedDipole;
    public double[][][] inducedDipoleCR;
    private double[][][] optDipole;
    private double[][][] optDipoleCR;
    private double[][] cartesianDipolePhi;
    private double[][] cartesianDipolePhiCR;

    /**
     * Field array for each thread. [threadID][X/Y/Z][atomID]
     */
    private double[][][] field;
    /**
     * Chain rule field array for each thread. [threadID][X/Y/Z][atomID]
     */
    private double[][][] fieldCR;

    /**
     * Flag to indicate use of generalized Kirkwood.
     */
    private boolean generalizedKirkwoodTerm;
    private GeneralizedKirkwood generalizedKirkwood;
    private double aewald;
    private double aewald3;

    private int currentOptOrder;
    private final int maxThreads;
    private final OPTLoop[] optLoop;
    public final double[] optCoefficients;
    public final double[] optCoefficientsSum;

    public OPTRegion(int nt, int optOrder) {
        maxThreads = nt;
        optLoop = new OPTLoop[nt];
        optCoefficients = new double[optOrder + 1];
        optCoefficientsSum = new double[optOrder + 1];
        switch (optOrder) {
            case 1:
                optCoefficients[0] = 0.530;
                optCoefficients[1] = 0.604;
                break;
            case 2:
                optCoefficients[0] = 0.042;
                optCoefficients[1] = 0.635;
                optCoefficients[2] = 0.414;
                break;
            case 3:
                optCoefficients[0] = -0.132;
                optCoefficients[1] = 0.218;
                optCoefficients[2] = 0.637;
                optCoefficients[3] = 0.293;
                break;
            case 4:
                optCoefficients[0] = -0.071;
                optCoefficients[1] = -0.096;
                optCoefficients[2] = 0.358;
                optCoefficients[3] = 0.587;
                optCoefficients[4] = 0.216;
                break;
            case 5:
                optCoefficients[0] = -0.005;
                optCoefficients[1] = -0.129;
                optCoefficients[2] = -0.026;
                optCoefficients[3] = 0.465;
                optCoefficients[4] = 0.528;
                optCoefficients[5] = 0.161;
                break;
            case 6:
                optCoefficients[0] = 0.014;
                optCoefficients[1] = -0.041;
                optCoefficients[2] = -0.172;
                optCoefficients[3] = 0.073;
                optCoefficients[4] = 0.535;
                optCoefficients[5] = 0.467;
                optCoefficients[6] = 0.122;
                break;
            default:
                logger.severe(" Unsupported OPT order.");
        }

        for (int i = 0; i <= optOrder; i++) {
            for (int j = optOrder; j >= i; j--) {
                optCoefficientsSum[i] += optCoefficients[j];
            }
        }
    }

    public void init(int currentOptOrder, Atom[] atoms, double[] polarizability,
                     double[][][] inducedDipole, double[][][] inducedDipoleCR,
                     double[][][] optDipole, double[][][] optDipoleCR,
                     double[][] cartesianDipolePhi, double[][] cartesianDipolePhiCR,
                     double[][][] field, double[][][] fieldCR,
                     boolean generalizedKirkwoodTerm, GeneralizedKirkwood generalizedKirkwood,
                     ParticleMeshEwaldCart.EwaldParameters ewaldParameters) {
        this.currentOptOrder = currentOptOrder;
        this.atoms = atoms;
        this.polarizability = polarizability;
        this.inducedDipole = inducedDipole;
        this.inducedDipoleCR = inducedDipoleCR;
        this.optDipole = optDipole;
        this.optDipoleCR = optDipoleCR;
        this.cartesianDipolePhi = cartesianDipolePhi;
        this.cartesianDipolePhiCR = cartesianDipolePhiCR;
        this.field = field;
        this.fieldCR = fieldCR;
        this.generalizedKirkwoodTerm = generalizedKirkwoodTerm;
        this.generalizedKirkwood = generalizedKirkwood;
        this.aewald = ewaldParameters.aewald;
        this.aewald3 = ewaldParameters.aewald3;
    }

    @Override
    public void run() throws Exception {
        try {
            int ti = getThreadIndex();
            if (optLoop[ti] == null) {
                optLoop[ti] = new OPTRegion.OPTLoop();
            }
            int nAtoms = atoms.length;
            execute(0, nAtoms - 1, optLoop[ti]);
        } catch (RuntimeException ex) {
            logger.warning("Fatal exception computing the opt induced dipoles in thread " + getThreadIndex());
            throw ex;
        } catch (Exception e) {
            String message = "Fatal exception computing the opt induced dipoles in thread " + getThreadIndex() + "\n";
            logger.log(Level.SEVERE, message, e);
        }

    }

    private class OPTLoop extends IntegerForLoop {

        @Override
        public IntegerSchedule schedule() {
            return IntegerSchedule.fixed();
        }

        @Override
        public void run(int lb, int ub) throws Exception {
            final double[][] induced0 = inducedDipole[0];
            final double[][] inducedCR0 = inducedDipoleCR[0];

            // Reduce the real space field.
            for (int i = lb; i <= ub; i++) {
                double fx = 0.0;
                double fy = 0.0;
                double fz = 0.0;
                double fxCR = 0.0;
                double fyCR = 0.0;
                double fzCR = 0.0;
                for (int j = 1; j < maxThreads; j++) {
                    fx += field[j][0][i];
                    fy += field[j][1][i];
                    fz += field[j][2][i];
                    fxCR += fieldCR[j][0][i];
                    fyCR += fieldCR[j][1][i];
                    fzCR += fieldCR[j][2][i];
                }
                field[0][0][i] += fx;
                field[0][1][i] += fy;
                field[0][2][i] += fz;
                fieldCR[0][0][i] += fxCR;
                fieldCR[0][1][i] += fyCR;
                fieldCR[0][2][i] += fzCR;
            }
            if (aewald > 0.0) {
                // Add the self and reciprocal space fields to the real space field.
                for (int i = lb; i <= ub; i++) {
                    double[] dipolei = induced0[i];
                    double[] dipoleCRi = inducedCR0[i];
                    final double[] phii = cartesianDipolePhi[i];
                    final double[] phiCRi = cartesianDipolePhiCR[i];
                    double fx = aewald3 * dipolei[0] - phii[t100];
                    double fy = aewald3 * dipolei[1] - phii[t010];
                    double fz = aewald3 * dipolei[2] - phii[t001];
                    double fxCR = aewald3 * dipoleCRi[0] - phiCRi[t100];
                    double fyCR = aewald3 * dipoleCRi[1] - phiCRi[t010];
                    double fzCR = aewald3 * dipoleCRi[2] - phiCRi[t001];
                    field[0][0][i] += fx;
                    field[0][1][i] += fy;
                    field[0][2][i] += fz;
                    fieldCR[0][0][i] += fxCR;
                    fieldCR[0][1][i] += fyCR;
                    fieldCR[0][2][i] += fzCR;
                }
            }
            if (generalizedKirkwoodTerm) {
                SharedDoubleArray[] gkField = generalizedKirkwood.sharedGKField;
                SharedDoubleArray[] gkFieldCR = generalizedKirkwood.sharedGKFieldCR;

                // Add the GK reaction field to the intramolecular field.
                for (int i = lb; i <= ub; i++) {
                    field[0][0][i] += gkField[0].get(i);
                    field[0][1][i] += gkField[1].get(i);
                    field[0][2][i] += gkField[2].get(i);
                    fieldCR[0][0][i] += gkFieldCR[0].get(i);
                    fieldCR[0][1][i] += gkFieldCR[1].get(i);
                    fieldCR[0][2][i] += gkFieldCR[2].get(i);
                }
            }

            // Collect the current Opt Order induced dipole.
            for (int i = lb; i <= ub; i++) {
                final double[] ind = induced0[i];
                final double[] indCR = inducedCR0[i];
                final double polar = polarizability[i];
                for (int j = 0; j < 3; j++) {
                    optDipole[currentOptOrder][i][j] = polar * field[0][j][i];
                    optDipoleCR[currentOptOrder][i][j] = polar * fieldCR[0][j][i];
                    ind[j] = optDipole[currentOptOrder][i][j];
                    indCR[j] = optDipoleCR[currentOptOrder][i][j];
                }
            }
        }
    }
}
