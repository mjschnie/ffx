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
package ffx.numerics.switching;

import static org.apache.commons.math3.util.FastMath.max;
import static org.apache.commons.math3.util.FastMath.min;

/**
 * TODO: Description by Jacob and Rae.
 *
 * @author Jacob M. Litman
 * @author Rae Corrigan
 */
public class BellCurveSwitch implements UnivariateSwitchingFunction {
    private double off;
    private double cut;
    private final double midpoint;
    private UnivariateSwitchingFunction switchingFunction;
    private UnivariateSwitchingFunction secondSwitchingFunction;

    public BellCurveSwitch(double midpoint) {
        this.midpoint = midpoint;

        double start = max((midpoint - 0.5), 0.0);
        double end = min(midpoint + 0.5, 1.0);

        this.off = start;
        this.cut = end;

        switchingFunction = new MultiplicativeSwitch(start, this.midpoint);
        secondSwitchingFunction = new MultiplicativeSwitch(this.midpoint, end);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getZeroBound() {
        return min(off, cut);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getOneBound() {
        return max(cut, off);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean constantOutsideBounds() {
        return switchingFunction.constantOutsideBounds() && secondSwitchingFunction.constantOutsideBounds();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validOutsideBounds() {
        return switchingFunction.validOutsideBounds() && secondSwitchingFunction.validOutsideBounds();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getHighestOrderZeroDerivative() {
        return Math.min(switchingFunction.getHighestOrderZeroDerivative(), secondSwitchingFunction.getHighestOrderZeroDerivative());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean symmetricToUnity() {
        return switchingFunction.symmetricToUnity() && secondSwitchingFunction.symmetricToUnity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double valueAt(double x) throws IllegalArgumentException {
        // x is input lambda value
        if (x > midpoint) {
            //second switch
            return secondSwitchingFunction.valueAt(x);
        } else {
            //first switch
            return switchingFunction.valueAt(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double firstDerivative(double x) {
        if (x > midpoint) {
            return secondSwitchingFunction.firstDerivative(x);
        } else {
            return switchingFunction.firstDerivative(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double secondDerivative(double x) {
        if (x > midpoint) {
            return secondSwitchingFunction.secondDerivative(x);
        } else {
            return switchingFunction.secondDerivative(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double nthDerivative(double x, int order) throws IllegalArgumentException {
        if (x > midpoint) {
            return secondSwitchingFunction.nthDerivative(x, order);
        } else {
            return switchingFunction.nthDerivative(x, order);
        }
    }
}
