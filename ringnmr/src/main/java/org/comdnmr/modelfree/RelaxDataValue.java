/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.comdnmr.modelfree;

import java.util.Random;
import static org.comdnmr.modelfree.RelaxEquations.I;
import static org.comdnmr.modelfree.RelaxEquations.ImS;
import static org.comdnmr.modelfree.RelaxEquations.IpS;

/**
 *
 * @author brucejohnson
 */
public class RelaxDataValue  {

    final MolDataValues molDataValue; final double R1;
    final double R1err;
    final double R2;
    final double R2err;
    final RelaxEquations relaxObj;

    public RelaxDataValue(MolDataValues molDataValue, double r1,
                          double r1Error, double r2, double r2Error,
                          RelaxEquations relaxObj) {
        this.molDataValue = molDataValue;
        this.R1 = r1;
        this.R1err = r1Error;
        this.R2 = r2;
        this.R2err = r2Error;
        this.relaxObj = relaxObj;
    }


    public MolDataValues getMolData() {
        return molDataValue;
    }

}
