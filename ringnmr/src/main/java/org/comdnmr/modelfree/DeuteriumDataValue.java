package org.comdnmr.modelfree;

import java.util.Random;

public class DeuteriumDataValue extends RelaxDataValue {
    final double rQ;
    final double rQError;
    final double rAP;
    final double rAPError;

    public DeuteriumDataValue(MolDataValues molDataValue, double r1,
                              double r1Error, double r2, double r2Error,
                              double rQ, double rQError, double rAP, double rAPError,
                              RelaxEquations relaxObj) {
        super(molDataValue, r1, r1Error, r2, r2Error, relaxObj);
        this.rAP = rAP;
        this.rAPError = rAPError;
        this.rQ = rQ;
        this.rQError = rQError;
    }

    public void randomize(MolDataValues molData, double r1, double r2,
                          double rQ, double rAP, Random random, double scale) {
        double newR1 = r1 + random.nextGaussian() * scale * R1err;
        double newR2 = r2 + random.nextGaussian() * scale * R2err;
        double newRQ = rQ + random.nextGaussian() * scale * rQError;
        double newRAP = rAP + random.nextGaussian() * scale * rAPError;
        RelaxDataValue newValue = new DeuteriumDataValue(molData, newR1, R1err,
                newR2, R2err, newRQ, rQError, newRAP, rAPError, relaxObj);
        molData.addData(newValue);
    }

}
