package ffx.numerics.switching;

public class NoLambdaDependenceSwitch implements UnivariateSwitchingFunction {

    //TODO: check on returns for getZeroBound and getOneBound
    /**
     * {@inheritDoc}
     */
    @Override
    public double getZeroBound() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getOneBound() {
        return 0;
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
    public double valueAt(double x) throws IllegalArgumentException {
        return 1.0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double firstDerivative(double x) {
        return 0.0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double secondDerivative(double x){
        return 0.0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double nthDerivative(double x, int order) throws IllegalArgumentException{
        return 0.0;
    }
}
