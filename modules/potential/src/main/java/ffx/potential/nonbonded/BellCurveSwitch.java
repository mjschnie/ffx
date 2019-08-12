package ffx.potential.nonbonded;

import ffx.numerics.switching.UnivariateSwitchingFunction;

public class BellCurveSwitch implements UnivariateSwitchingFunction {
    private double off;
    private double cut;
    private final double midpoint;
    private UnivariateSwitchingFunction switchingFunction;
    private UnivariateSwitchingFunction secondSwitchingFunction;

    public BellCurveSwitch(double midpoint){
        this.midpoint = midpoint;

        double start;
        if ((midpoint - 0.5) < 0.0) {
            start = 0.0;
        } else {
            start = midpoint - 0.5;
        }
        double end = Math.min(midpoint + 0.5, 1.0);

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
        return off < cut ? off : cut;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getOneBound() {
        return cut > off ? cut : off;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean constantOutsideBounds() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validOutsideBounds() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getHighestOrderZeroDerivative() {
        return 2;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean symmetricToUnity() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double valueAt(double x) throws IllegalArgumentException{
        // x is input lambda value
        if(x>midpoint){
            //second switch
            return secondSwitchingFunction.valueAt(x);
        } else{
            //first switch
            return switchingFunction.valueAt(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double firstDerivative(double x){
        if(x>midpoint){
            return secondSwitchingFunction.firstDerivative(x);
        } else{
            return switchingFunction.firstDerivative(x);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double secondDerivative(double x) {
        if(x>midpoint){
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
        if(x > midpoint){
            return secondSwitchingFunction.nthDerivative(x,order);
        } else{
            return switchingFunction.nthDerivative(x,order);
        }
    }
}
