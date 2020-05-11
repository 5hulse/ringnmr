package org.comdnmr.modelfree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.math3.geometry.euclidean.threed.NotARotationMatrixException;
import org.apache.commons.math3.geometry.euclidean.threed.Rotation;
import org.apache.commons.math3.geometry.euclidean.threed.RotationConvention;
import org.apache.commons.math3.geometry.euclidean.threed.RotationOrder;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.optim.PointValuePair;
import org.comdnmr.data.Fitter;
import static org.comdnmr.modelfree.RelaxFit.ExptType.NOE;
import static org.comdnmr.modelfree.RelaxFit.ExptType.R1;
import static org.comdnmr.modelfree.RelaxFit.ExptType.R2;
import static org.comdnmr.modelfree.RelaxFit.DiffusionType.ANISOTROPIC;
import static org.comdnmr.modelfree.RelaxFit.DiffusionType.OBLATE;
import static org.comdnmr.modelfree.RelaxFit.DiffusionType.PROLATE;
import static org.comdnmr.modelfree.RelaxFit.DiffusionType.ISOTROPIC;
import org.comdnmr.modelfree.RelaxEquations;

/**
 *
 * @author brucejohnson
 */
public class RelaxFit {

    boolean reportFitness = true;
    int reportAt = 10;
    long startTime = 0;
    double B0;
    double[][] xValues;
    double[] yValues;
    double[] errValues;
    double[] bestPars;
    double[] parErrs;
    double bestAIC;
    double bestChiSq;
    ArrayList<RelaxEquations> relaxObjs = new ArrayList<>();
    Fitter bestFitter;
    int[] residueModels;
    int[] resParStart;
    int[] nParsPerModel = {0, 1, 2, 0, 0, 3, 4};
    static ExptType[] expTypeArray = {R1, R2, NOE};
    double[] tauMBounds = {3.8e-9, 4.5e-9};
    double[][] coords;
    DiffusionType diffusionType;
    static int ANISO_ANGLE_STARTS[][] = {
        {0, 0, 0},
        {1, 0, 0},
        {0, 1, 0},
        {1, 1, 0},
        {0, 0, 1},
        {1, 0, 1},
        {0, 1, 1},
        {1, 1, 1}
    };
    static int ANGLE_STARTS[][] = {
        {0, 0},
        {1, 0},
        {0, 1},
        {1, 1}
    };

    public static double[] getDValues(double isoD) {
        double[] anisoD = {0.75 * isoD, isoD, 1.25 * isoD};
        return anisoD;
    }

    public void setXYE(double[][] xValues, double[] yValues, double[] errValues) {
        this.xValues = xValues;
        this.yValues = yValues;
        this.errValues = errValues;
    }

    public void setCoords(double[][] coords) {
        this.coords = coords;
    }

    public void setDiffusionType(DiffusionType type) {
        this.diffusionType = type;
    }

    public enum ExptType {
        R1, R2, NOE;
    }

    public enum DiffusionType {
        ISOTROPIC(1, 0) {
            @Override
            public double[] getGuess(double isoD) {
                double[] guess = {isoD};
                return guess;
            }

            public double[] getAngles(int iStart) {
                return new double[0];
            }
        },
        PROLATE(2, 2) {
            @Override
            public double[] getGuess(double isoD) {
                double[] guess = {0.75 * isoD, 1.25 * isoD};
                return guess;
            }

            public double[] getAngles(int iStart) {
                int[] jAng = ANGLE_STARTS[iStart];
                double[] angles = new double[2];
                for (int i = 0; i < 2; i++) {
                    angles[i] = (Math.PI * (jAng[i] * 2 + 1)) / 4;
                }
                return angles;
            }
        }, OBLATE(2, 2) {
            @Override
            public double[] getGuess(double isoD) {
                double[] guess = {0.75 * isoD, 1.25 * isoD};
                return guess;
            }

            public double[] getAngles(int iStart) {
                int[] jAng = ANGLE_STARTS[iStart];
                double[] angles = new double[2];
                for (int i = 0; i < 2; i++) {
                    angles[i] = (Math.PI * (jAng[i] * 2 + 1)) / 4;
                }
                return angles;
            }
        }, ANISOTROPIC(3, 3) {
            @Override
            public double[] getGuess(double isoD) {
                double[] anisoD = {0.75 * isoD, isoD, 1.25 * isoD};
                return anisoD;
            }

            public double[] getAngles(int iStart) {
                int[] jAng = ANISO_ANGLE_STARTS[iStart];
                double[] angles = new double[3];
                for (int i = 0; i < 3; i++) {
                    angles[i] = (Math.PI * (jAng[i] * 2 + 1)) / 4;
                }
                return angles;
            }
        };

        int nAnglePars;
        int nDiffPars;
        int nAngleGuesses;

        DiffusionType(int nDiffPars, int nAnglePars) {
            this.nAnglePars = nAnglePars;
            this.nDiffPars = nDiffPars;
            this.nAngleGuesses = (int) Math.pow(2, nAnglePars);
        }

        public abstract double[] getGuess(double isoD);

        public abstract double[] getAngles(int iStart);

        public int getNAnglePars() {
            return nAnglePars;
        }

        public int getNDiffusionPars() {
            return nDiffPars;
        }

        public int getNAngleGuesses() {
            return nAngleGuesses;
        }

    }

    public void makeRelaxObjs(double[] fields, String elem1, String elem2) {
        for (int i = 0; i < fields.length; i++) {
            relaxObjs.add(new RelaxEquations(fields[i], elem1, elem2));
        }
    }

    public double[] getJ(double[] pars, RelaxEquations relaxObj, int modelNum) {
        double tauM = pars[0];//4.5e-9;
        double s2 = pars[1];
        double[] J = new double[5];
        switch (modelNum) {
            case 1:
                J = relaxObj.getJModelFree(tauM, s2);
                break;
            case 2:
                double tau = pars[2];
                J = relaxObj.getJModelFree(tau, tauM, s2);
                break;
            case 5:
                tau = pars[2];
                double sf2 = pars[3];
                J = relaxObj.getJModelFree(tau, tauM, s2, sf2);
                break;
            case 6:
                tau = pars[2];
                sf2 = pars[3];
                double tauS = pars[4];
//                System.out.println("tau, sf2, tauS = " + tau + " " + sf2 + " " + tauS);
                J = relaxObj.getJModelFree(tau, tauM, tauS, s2, sf2);
                break;
            default:
                break;
        }
//        for (double Jval : J) {
//            System.out.println("J: " + Jval);
//        }
        return J;
    }

    public double[] getJDiffusion(double[] pars, RelaxEquations relaxObj, int modelNum, double[] v, DiffusionType dType, double[][] D, double[][] VT) {
        int nEqlDiffPars = 0;
        int nNullAngles = 0;
        if (dType == PROLATE || dType == OBLATE) { //Dxx = Dyy or Dyy = Dzz
            nEqlDiffPars = 1;
            nNullAngles = 1;
        }

        if (D == null && VT == null) {
            double Dxx = pars[0];
            double Dyy = pars[1];
            double Dzz = 0.0;
            switch (dType) {
                case PROLATE:
                    //Dxx = Dyy
                    nEqlDiffPars = 1;
                    Dyy = pars[1 - nEqlDiffPars];
                    Dzz = pars[2 - nEqlDiffPars];
                    break;
                case OBLATE:
                    //Dyy = Dzz
                    nEqlDiffPars = 1;
                    Dzz = pars[2 - nEqlDiffPars];
                    break;
                case ANISOTROPIC:
                    nEqlDiffPars = 0;
                    Dxx = pars[0];
                    Dyy = pars[1];
                    Dzz = pars[2];
                    break;
            }
            double[][] D1 = {{Dxx, 0.0, 0.0},
            {0.0, Dyy, 0.0},
            {0.0, 0.0, Dzz}};

            Rotation rot = getDRotation(pars, dType);
            D = D1;
            VT = getRotationMatrix(rot);
//            System.out.println("Rotated Deig = " + new Array2DRowRealMatrix(D).toString());
//            System.out.println("Rotated VTeig = " + new Array2DRowRealMatrix(VT).toString());
        }
        int nDiffPars = 6;
        double s2 = pars[nDiffPars - nEqlDiffPars - nNullAngles];
        double[] J = new double[5];
        switch (modelNum) {
            case 1:
                J = relaxObj.getJDiffusion(dType, D, VT, v, s2, null, null, null);
                break;
            case 2:
                double tau = pars[nDiffPars + 1 - nEqlDiffPars - nNullAngles];
                J = relaxObj.getJDiffusion(dType, D, VT, v, s2, tau, null, null);
                break;
            case 5:
                tau = pars[nDiffPars + 1 - nEqlDiffPars - nNullAngles];
                double sf2 = pars[nDiffPars + 2 - nEqlDiffPars - nNullAngles];
                J = relaxObj.getJDiffusion(dType, D, VT, v, s2, tau, sf2, null);
                break;
            case 6:
                tau = pars[nDiffPars + 1 - nEqlDiffPars - nNullAngles];
                sf2 = pars[nDiffPars + 2 - nEqlDiffPars - nNullAngles];
                double tauS = pars[nDiffPars + 3 - nEqlDiffPars - nNullAngles];
//                System.out.println("tau, sf2, tauS = " + tau + " " + sf2 + " " + tauS);
                J = relaxObj.getJDiffusion(dType, D, VT, v, s2, tau, sf2, tauS);
                break;
            default:
                break;
        }
//        for (double Jval : J) {
//            System.out.println("J: " + Jval);
//        }
        return J;
    }

    public Rotation getDRotation(double[] pars, DiffusionType dType) {
        int nEqlDiffPars = 0;
        double alpha = 0.0;
        double beta = 0.0;
        double gamma = 0.0;
        switch (dType) {
            case PROLATE:
                //Dxx = Dyy
                nEqlDiffPars = 1;
                alpha = pars[3 - nEqlDiffPars];
                beta = pars[4 - nEqlDiffPars];
                gamma = 0;
                break;
            case OBLATE:
                //Dyy = Dzz
                nEqlDiffPars = 1;
                alpha = pars[3 - nEqlDiffPars];
                beta = pars[4 - nEqlDiffPars];
                gamma = 0;
                break;
            case ANISOTROPIC:
                nEqlDiffPars = 0;
                alpha = pars[3];
                beta = pars[4];
                gamma = pars[5];
                break;
        }
//        System.out.println("alpha = " + alpha*180./Math.PI + " beta = " + beta*180./Math.PI + " gamma = " + gamma*180./Math.PI);
        Rotation rot = null;
        try {
            rot = new Rotation(RotationOrder.ZYZ, RotationConvention.VECTOR_OPERATOR, alpha, beta, gamma);
        } catch (NotARotationMatrixException nE) {
            System.out.println("Can't create rot mat:" + nE.getMessage());
            double[][] rotMatCatch = rot.getMatrix();
            for (int i = 0; i < 3; i++) {
                rotMatCatch[1][i] = -rotMatCatch[1][i];
            }
            try {
                rot = new Rotation(rotMatCatch, 1e-6);
            } catch (NotARotationMatrixException nE2) {
                System.out.println("Can't create rot mat 2nd try:" + nE.getMessage());
                rot = null;
            }
        }
        return rot;
    }

    public double[][] getRotationMatrix(Rotation rot) {
        return new Array2DRowRealMatrix(rot.getMatrix()).transpose().getData();
    }

    public double[][][] rotateD(double[] resPars) {
        double[][][] resList = new double[2][3][3];
        double dx = resPars[0];
        double d1 = resPars[1];
        double d2 = 0.0;
        double dy = 0.0;
        double dz = 0.0;
        if (diffusionType == PROLATE) {
            dy = dx;
            dz = d1;
        } else if (diffusionType == OBLATE) {
            dy = d1;
            dz = d1;
        } else if (diffusionType == ANISOTROPIC) {
            dy = d1;
            dz = resPars[2];
        }
        double[][] D = {{dx, 0.0, 0.0}, {0.0, dy, 0.0}, {0.0, 0.0, dz}};
        Rotation rot = getDRotation(resPars, diffusionType);
        double[][] VT = getRotationMatrix(rot);//getVT();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                resList[0][i][j] = D[i][j];
                resList[1][i][j] = VT[i][j];
            }
        }

        return resList;
    }

    public double getYVal(double[] pars, RelaxEquations relaxObj, double[] J, ExptType type) {
        double y = 0.0;
        switch (type) {
            case R1:
                y = relaxObj.R1(J);
//                System.out.print("R1 = " + y + " ");
                break;
            case R2:
//                double Rex = pars[0];
                y = relaxObj.R2(J, 0.0);
//                System.out.print("R2 = " + y + " ");
                break;
            case NOE:
                y = relaxObj.NOE(J);
//                System.out.print("NOE = " + y + " ");
                break;
            default:
                break;
        }
        return y;
    }

    public double[][] getSimValues(double first, double last, int n, boolean adjust) {
        double[][] result = new double[2][n];
        double delta = (last - first) / (n - 1);
//        int i = 0;
        for (int i = 0; i < n; i++) {
            int iField = (int) xValues[i][0];
            RelaxEquations relaxObj = relaxObjs.get(iField);
            int iRes = (int) xValues[i][1];
            int iExpType = (int) xValues[i][2];
            int modelNum = residueModels[iRes];
            double[] J = getJ(bestPars, relaxObj, modelNum);
            ExptType type = expTypeArray[iExpType];
            double x = first + delta * i;
            double y = getYVal(bestPars, relaxObj, J, type);
            result[0][i] = x;
            result[1][i] = y;
        }
        return result;
    }

    public double value(double[] pars, double[][] values) {
        int n = values[0].length;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            int iField = (int) xValues[i][0];
            RelaxEquations relaxObj = relaxObjs.get(iField);
            int iRes = (int) xValues[i][1];
            int iExpType = (int) xValues[i][2];
            int modelNum = residueModels[iRes];
            double[] J = getJ(pars, relaxObj, modelNum);
            ExptType type = expTypeArray[iExpType];
            double y = getYVal(pars, relaxObj, J, type);
            double delta = y - values[2][i];
            sum += (delta * delta) / (errValues[i] * errValues[i]);
        }
        double rms = Math.sqrt(sum / n);

        return rms;

    }

    public double valueMultiResidue(double[] pars, double[][] values) {
        int n = values[0].length;
        double sum = 0.0;
        for (int i = 0; i < values[2].length; i++) {
            int iField = (int) xValues[i][0];
            RelaxEquations relaxObj = relaxObjs.get(iField);
            int iRes = (int) xValues[i][1];
            int iExpType = (int) xValues[i][2];
            int modelNum = residueModels[iRes];
            double[] resPars = new double[nParsPerModel[modelNum] + 1];
            resPars[0] = pars[0];
            int parStart = resParStart[iRes] + 1;
            System.arraycopy(pars, parStart, resPars, 1, nParsPerModel[modelNum]);
//            parStart += resParStart[i];
            double[] J = getJ(resPars, relaxObj, modelNum);
            ExptType type = expTypeArray[iExpType];
            double y = getYVal(resPars, relaxObj, J, type);
            double delta = y - values[2][i];
            sum += (delta * delta) / (errValues[i] * errValues[i]);
        }

        double rms = Math.sqrt(sum / n);

        return rms;

    }

    public double diffSqDiffusion(int iRes, int iExpType, int i, double[][] values,
            RelaxEquations relaxObj, double[] J, Map<Integer, double[]> expValMap, Map<Integer, double[]> expErrMap) {

        double val = 0.0;
        double chiSq = 0.0;
        if (!expValMap.containsKey(iRes)) {
            expValMap.put(iRes, new double[3]);
            expErrMap.put(iRes, new double[3]);
        }
        if (expValMap.get(iRes)[iExpType] == 0.0) {
            expValMap.get(iRes)[iExpType] = values[2][i];
            expErrMap.get(iRes)[iExpType] = errValues[i];
        }
        double r1 = expValMap.get(iRes)[0];
        double r2 = expValMap.get(iRes)[1];
        double noe = expValMap.get(iRes)[2];
        if (r1 != 0.0 && r2 != 0.0 && noe != 0.0) {
            double r1Err = expErrMap.get(iRes)[0];
            double r2Err = expErrMap.get(iRes)[1];
            double noeErr = expErrMap.get(iRes)[2];
            double rhoExp = relaxObj.calcRhoExp(r1, r2, noe, J);
            double rhoPred = relaxObj.calcRhoPred(J);
            double delta = rhoPred - rhoExp;
            System.out.println(r1 + " " + r2 + " " + noe);
            System.out.println(iRes + ": " + rhoExp + ", " + rhoPred + " " + delta);
            double error = relaxObj.calcRhoExpError(r1, r2, noe, J, r1Err, r2Err, noeErr, rhoExp);//(r1Err + r2Err + noeErr) / 3;//
            chiSq = (delta * delta) / (error * error);
            val = delta * delta;
        }
        return val;
    }

    public double valueDiffusion(double[] pars, double[][] values) {
        int n = values[0].length;
        double sum = 0.0;
        Map<Integer, double[]> expValMap = new HashMap<>();
        Map<Integer, double[]> expErrMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int iField = (int) xValues[i][0];
            RelaxEquations relaxObj = relaxObjs.get(iField);
            int iRes = (int) xValues[i][1];
            int iExpType = (int) xValues[i][2];
            int iCoord = (int) xValues[i][3];
            int modelNum = residueModels[iRes];
            double[] v = coords[iCoord];
            double[] J = getJDiffusion(pars, relaxObj, modelNum, v, diffusionType, null, null);
            sum += diffSqDiffusion(iRes, iExpType, i, values, relaxObj, J, expValMap, expErrMap);
        }
        double rms = Math.sqrt(sum / n / 3);

        return rms;

    }

    public double valueDiffusionMultiResidue(double[] pars, double[][] values) {
        int n = values[0].length;
        double sum = 0.0;
        Map<Integer, double[]> expValMap = new HashMap<>();
        Map<Integer, double[]> expErrMap = new HashMap<>();
        for (int i = 0; i < values[2].length; i++) {
            int iField = (int) xValues[i][0];
            RelaxEquations relaxObj = relaxObjs.get(iField);
            int iRes = (int) xValues[i][1];
            int iExpType = (int) xValues[i][2];
            int iCoord = (int) xValues[i][3];
            int modelNum = residueModels[iRes];
            int nDiffPars = 6;
            if (diffusionType == OBLATE || diffusionType == PROLATE) {
                nDiffPars = 4;
            }
            double[] resPars = new double[nParsPerModel[modelNum] + nDiffPars];
            for (int j = 0; j < nDiffPars; j++) {
                resPars[j] = pars[j];
            }
            int parStart = resParStart[iRes] + nDiffPars;
            System.arraycopy(pars, parStart, resPars, nDiffPars, nParsPerModel[modelNum]);
//            parStart += resParStart[i];
            double[] v = coords[iCoord];
            double[] J = getJDiffusion(resPars, relaxObj, modelNum, v, diffusionType, null, null);
            sum += diffSqDiffusion(iRes, iExpType, i, values, relaxObj, J, expValMap, expErrMap);
        }

        double rms = Math.sqrt(sum / n);

        return rms;

    }

    public double getRMSDIso(double[][] D) {
        int n = yValues.length;
        double[][] values = new double[3][n];
        System.arraycopy(yValues, 0, values[2], 0, n);
        double sumIso = 0.0;
        double chiSqIso = 0.0;
        Map<Integer, double[]> isoValMap = new HashMap<>();
        Map<Integer, double[]> expErrMap = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int iField = (int) xValues[i][0];
            RelaxEquations relaxObj = relaxObjs.get(iField);
            int iRes = (int) xValues[i][1];
            int iExpType = (int) xValues[i][2];
            int iCoord = (int) xValues[i][3];
            int modelNum = residueModels[iRes];
            int nDiffPars = 6;
            if (diffusionType == OBLATE || diffusionType == PROLATE) {
                nDiffPars = 4;
            }
            double[] resPars = new double[nParsPerModel[modelNum] + nDiffPars];
            for (int p = 0; p < D.length; p++) {
                resPars[p] = D[p][p];
            }
            for (int j = nDiffPars - 3; j < nDiffPars; j++) {
                resPars[j] = 0.0;
            }
            resPars[resPars.length - 1] = 1.0; //Model 0: S2 = 1.0, all others null.
            double[] v = coords[iCoord];
            double[] J = getJDiffusion(resPars, relaxObj, modelNum, v, ISOTROPIC, D, D);
            sumIso += diffSqDiffusion(iRes, iExpType, i, values, relaxObj, J, isoValMap, expErrMap);

        }
        double rms = Math.sqrt(sumIso / n / 3);

        return rms;

    }

    public double valueDMat(double[] pars, double[][] values) {
        //fixme first 3 pars are NOT the values of the diagonlized D, but should be
        //angles wrong as well because of this??
        if (diffusionType == OBLATE || diffusionType == PROLATE) {
            Arrays.sort(pars, 0, 2);
        } else if (diffusionType == ANISOTROPIC) {
            Arrays.sort(pars, 0, 3);
        }
//        for (int p=0; p<9; p++) {
//            System.out.println("pars " + p + " = " + pars[p]);
//        }
        double[][][] rotResults = rotateD(pars);
        double[][] D = rotResults[0];
        double[][] VT = rotResults[1];
        int n = values[0].length;
        double sum = 0.0;
        for (int i = 0; i < n; i += 3) {
            int iField = (int) xValues[i][0];
            RelaxEquations relaxObj = relaxObjs.get(iField);
            int iRes = (int) xValues[i][1];
            int iCoord = (int) xValues[i][3];
            int modelNum = residueModels[iRes];
            int nDiffPars = 6;
            if (diffusionType == OBLATE || diffusionType == PROLATE) {
                nDiffPars = 4;
            }
            double[] resPars = new double[nParsPerModel[modelNum] + nDiffPars];
            for (int j = 0; j < nDiffPars; j++) {
                resPars[j] = pars[j];
//                System.out.println(resPars[j]);
            }
//            int parStart = resParStart[iRes] + nDiffPars;
//            System.arraycopy(pars, parStart, resPars, nDiffPars, nParsPerModel[modelNum]);
            resPars[resPars.length - 1] = 1.0; //Model 0: S2 = 1.0, all others null.
//            parStart += resParStart[i];
            double[] v = coords[iCoord];
            double[] J = getJDiffusion(resPars, relaxObj, modelNum, v, diffusionType, D, VT);
            double r1 = values[2][i];
            double r2 = values[2][i + 1];
            double noe = values[2][i + 2];
//            double r1Err = errValues[i];
//            double r2Err = errValues[i+1];
//            double noeErr = errValues[i+2];
            double rhoExp = relaxObj.calcRhoExp(r1, r2, noe, J);
            double rhoPred = relaxObj.calcRhoPred(J);
            double delta = rhoPred - rhoExp;
//            System.out.println("iField, iRes = " + iField + ", " + iRes);
//            System.out.println("Fit R1, R2, NOE: " + r1 + ", " + r2 + ", " + noe);
//            System.out.println("RhoExp, RhoPred, delta: " + rhoExp + ", " + rhoPred + ", " + delta);
//            double error = relaxObj.calcRhoExpError(r1, r2, noe, J, r1Err, r2Err, noeErr, rhoExp);//(r1Err + r2Err + noeErr) / 3;//
//            sum += (delta * delta) / (error * error);
            sum += delta * delta;
//            System.out.println("sum = " + sum);
        }
        double rms = Math.sqrt(sum / n);

        return rms;

    }

    public double[] calcYVals(double[] pars, boolean multiRes, boolean diffusion, boolean Drefine) {
        int n = yValues.length;
        double[] calcY = new double[n];
        for (int i = 0; i < n; i++) {
            int iField = (int) xValues[i][0];
            RelaxEquations relaxObj = relaxObjs.get(iField);
            int iRes = (int) xValues[i][1];
            int iExpType = (int) xValues[i][2];
            int iCoord = (int) xValues[i][3];
            int modelNum = residueModels[iRes];
            double[] J;
            double[][] D = null;
            double[][] VT = null;
            if (diffusion) {
                double[] v = coords[iCoord];
                double[] resPars;
                if (Drefine) {
                    resPars = new double[pars.length + 1];
                    for (int j = 0; j < pars.length; j++) {
                        resPars[j] = pars[j];
                    }
                    resPars[resPars.length - 1] = 1.0;
                    double[][][] rotResults = rotateD(resPars);
                    D = rotResults[0];
                    VT = rotResults[1];
//                    System.out.println("yVal D = " + new Array2DRowRealMatrix(D).toString());
//                    System.out.println("yVal VT = " + new Array2DRowRealMatrix(VT).toString());
                } else {
                    resPars = pars;
                }
                J = getJDiffusion(resPars, relaxObj, modelNum, v, diffusionType, D, VT);
            } else {
                J = getJ(pars, relaxObj, modelNum);
            }
            ExptType type = expTypeArray[iExpType];
            double y = getYVal(pars, relaxObj, J, type);
            if (multiRes) {
                double[] resPars;
                if (diffusion) {
                    int nDiffPars = 6;
                    if (diffusionType == OBLATE || diffusionType == PROLATE) {
                        nDiffPars = 4;
                    }
                    resPars = new double[nParsPerModel[modelNum] + nDiffPars];
                    for (int j = 0; j < nDiffPars; j++) {
                        resPars[j] = pars[j];
                    }
                    if (Drefine) {
                        resPars[resPars.length - 1] = 1.0;
                        double[][][] rotResults = rotateD(resPars);
                        D = rotResults[0];
                        VT = rotResults[1];
                    } else {
                        int parStart = resParStart[iRes] + nDiffPars;
                        System.arraycopy(pars, parStart, resPars, nDiffPars, nParsPerModel[modelNum]);
                    }
                    double[] v = coords[iCoord];
                    J = getJDiffusion(resPars, relaxObj, modelNum, v, diffusionType, D, VT);
                } else {
                    resPars = new double[nParsPerModel[modelNum] + 1];
                    resPars[0] = pars[0];
                    int parStart = resParStart[iRes] + 1;
                    System.arraycopy(pars, parStart, resPars, 1, nParsPerModel[modelNum]);
                    //            parStart += resParStart[i];
                    J = getJ(resPars, relaxObj, modelNum);
                }
                y = getYVal(resPars, relaxObj, J, type);
            }
            calcY[i] = y;
        }

        return calcY;
    }

    public double[] getPars() {
        return bestPars;
    }

    public double getAIC() {
        return bestAIC;
    }

    public double getChiSq() {
        return bestChiSq;
    }

    public double[] getParErrs() {
        return parErrs;
    }

    public double[][] getXVals() {
        return xValues;
    }

    public double[] getYVals() {
        return yValues;
    }

    public double[] getErrVals() {
        return errValues;
    }

    public double[][] getCoords() {
        return coords;
    }

    public Fitter getFitter() {
        return bestFitter;
    }

    public int[] getResParStart() {
        return resParStart;
    }

    public int[] getResidueModels() {
        return residueModels;
    }

    public ArrayList<RelaxEquations> getRelaxObjs() {
        return relaxObjs;
    }

    public void setResidueModels(int[] bestModels, boolean diffusion) {
        residueModels = bestModels;
        resParStart = new int[residueModels.length];
        int nParsPrev = 0;
        resParStart[0] = 0;
        for (int i = 1; i < residueModels.length; i++) {
            resParStart[i] = nParsPerModel[residueModels[i - 1]] + nParsPrev;
            nParsPrev = resParStart[i];
//            System.out.println(i + " resModel " + residueModels[i] + " resParStart " + resParStart[i]);
        }
    }

    public void setTauMBounds(double[] bounds) {
        tauMBounds[0] = bounds[0];
        tauMBounds[1] = bounds[1];
    }

    public PointValuePair fit(double sf, double[] guesses, boolean globalFit, boolean diffusion) {
        Fitter fitter;
        this.B0 = sf * 2.0 * Math.PI / RelaxEquations.GAMMA_H;
        if (globalFit && diffusion) {
            fitter = Fitter.getArrayFitter(this::valueDiffusionMultiResidue);
        } else if (globalFit && !diffusion) {
            fitter = Fitter.getArrayFitter(this::valueMultiResidue);
        } else if (!globalFit && diffusion) {
            fitter = Fitter.getArrayFitter(this::valueDiffusion);
        } else {
            fitter = Fitter.getArrayFitter(this::value);
        }
        double[] xVals0 = new double[yValues.length];
        double[][] xValues2 = {xVals0, xVals0};//{xValues[0], xValues[1]};
        fitter.setXYE(xValues2, yValues, errValues);
        double[] start = guesses; //{max0, r0, max1, 10.0};
        double[] lower = new double[guesses.length]; //{max0 / 2.0, r0 / 2.0, max1 / 2.0, 1.0};
        double[] upper = new double[guesses.length]; //{max0 * 2.0, r0 * 2.0, max1 * 2.0, 100.0};
        for (int i = 0; i < lower.length; i++) {
            lower[i] = guesses[i] / 20.0;
            upper[i] = guesses[i] * 20.0;
        }
        int iS2diff = 6;
        //bounds for the global fit parameters (e.g. tauM, Dxx, Dyy, Dzz, alpha, 
        //beta, gamma), where the indices in the parameter array are always the
        //same regardless of the model used.
        if (diffusion) {
            if (diffusionType == OBLATE || diffusionType == PROLATE) {
                int nEqlDiffPars = 1;
                int nNullAngles = 1;
                int iAlpha = 3 - nEqlDiffPars;
                int iBeta = 4 - nEqlDiffPars;
                lower[iAlpha] = 0.0;
                upper[iAlpha] = Math.PI / 2;
                lower[iBeta] = 0.0;
                upper[iBeta] = Math.PI / 2;
                iS2diff -= (nEqlDiffPars + nNullAngles);
            } else { //anisotropic
                int iAlpha = 3;
                int iBeta = 4;
                int iGamma = 5;
                lower[iAlpha] = 0.0;
                upper[iAlpha] = Math.PI / 2;
                lower[iBeta] = 0.0;
                upper[iBeta] = Math.PI / 2;
                lower[iGamma] = 0.0;
                upper[iGamma] = Math.PI / 2;
            }
        } else {
            int iTauM = 0;
            lower[iTauM] = tauMBounds[0];
            upper[iTauM] = tauMBounds[1];
        }
        //bounds for the other fit parameters (e.g. S2, tau, Sf2, tauS), which 
        //can have different indices in the parameter array depending on the 
        //model used and individual vs. global fitting.
        int iS2;
        if (globalFit) {
            for (int i = 0; i < resParStart.length; i++) {
                int modelNum = residueModels[i];
                if (diffusion) {
                    iS2 = resParStart[i] + iS2diff;
                } else {
                    iS2 = resParStart[i] + 1;
                }
                lower[iS2] = 0.0;
                upper[iS2] = 1.0;
                if (modelNum >= 5) {
                    int iS2f = iS2 + 2;
                    lower[iS2f] = 0.0;
                    upper[iS2f] = 1.0;
                }
            }
        } else {
            if (diffusion) {
                iS2 = iS2diff;
            } else {
                iS2 = 1;
            }
            lower[iS2] = 0.0;
            upper[iS2] = 1.0;
            int iS2f = iS2 + 2;
            if (lower.length > iS2f) {
                lower[iS2f] = 0.0;
                upper[iS2f] = 1.0;
            }
        }
//        for (int i=0; i<guesses.length; i++) {
//            System.out.println("guess lower upper " + i + " " + guesses[i] + " " + lower[i] + " " + upper[i]);
//        }
        try {
            PointValuePair result = fitter.fit(start, lower, upper, 10.0);
            bestPars = result.getPoint();
            bestChiSq = result.getValue();
            int k = bestPars.length;
            int n = yValues.length;
            bestAIC = n * Math.log(bestChiSq) + 2 * k;
//            bestAIC += (2*k*k + 2*k)/(n - k - 1); //corrected AIC for small sample sizes
            bestFitter = fitter;
            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

    }

    public void calcParErrs(PointValuePair result, Fitter fitter, double[] fitPars, boolean globalFit, boolean diffusion, boolean Drefine) {
        double[] yCalc;
        yCalc = calcYVals(fitPars, globalFit, diffusion, Drefine);
        parErrs = fitter.bootstrap(result.getPoint(), 300, true, yCalc);
    }

    public double calcD(double sf, double[] pars) {
        Fitter fitter = Fitter.getArrayFitter(this::valueDMat);
        this.B0 = sf * 2.0 * Math.PI / RelaxEquations.GAMMA_H;
        double[] xVals0 = new double[yValues.length];
        double[][] xValues2 = {xVals0, xVals0};//{xValues[0], xValues[1]};
        fitter.setXYE(xValues2, yValues, errValues);
        return fitter.value(pars);
    }

    public PointValuePair fitD(double sf, double[] guesses) {
        Fitter fitter = Fitter.getArrayFitter(this::valueDMat);
        this.B0 = sf * 2.0 * Math.PI / RelaxEquations.GAMMA_H;
        double[] xVals0 = new double[yValues.length];
        double[][] xValues2 = {xVals0, xVals0};//{xValues[0], xValues[1]};
        fitter.setXYE(xValues2, yValues, errValues);
        double[] start = guesses; //{max0, r0, max1, 10.0};
        double[] lower = new double[guesses.length]; //{max0 / 2.0, r0 / 2.0, max1 / 2.0, 1.0};
        double[] upper = new double[guesses.length]; //{max0 * 2.0, r0 * 2.0, max1 * 2.0, 100.0};
        for (int i = 0; i < lower.length; i++) {
            lower[i] = guesses[i] / 2;//1.0005;//20.0;
            upper[i] = guesses[i] * 2;//1.0005;//20.0;
        }
        int nDiffPars = diffusionType.getNDiffusionPars();
        int nAnglePars = diffusionType.getNAnglePars();
        double lba = 0.0;
        double uba = Math.PI / 2.0;
        System.out.println(diffusionType + " " + nDiffPars + " " + nAnglePars);
        for (int a = nDiffPars; a < nDiffPars + nAnglePars; a++) {
            lower[a] = guesses[a] - Math.PI / 4.0;
            upper[a] = guesses[a] + Math.PI / 4.0;
        }
        try {
            PointValuePair result = fitter.fit(start, lower, upper, 10.0);
            System.out.println("Scaled guess, bounds:");
            for (int i = 0; i < lower.length; i++) {
                double lb = lower[i];
                double ub = upper[i];
                double guess = guesses[i];
                if (i < nDiffPars) {
                    lb /= 1e7;
                    ub /= 1e7;
                    guess /= 1e7;
                } else {
                    lb = Math.toDegrees(lb);
                    ub = Math.toDegrees(ub);
                    guess = Math.toDegrees(guess);
                }
                System.out.printf("guess %7.3f LB %7.3f UB %7.3f\n", guess, lb, ub);
            }
            bestPars = result.getPoint();
            bestChiSq = result.getValue();
            int k = bestPars.length;
            int n = yValues.length;
            bestAIC = n * Math.log(bestChiSq) + 2 * k;
//            bestAIC += (2*k*k + 2*k)/(n - k - 1); //corrected AIC for small sample sizes
            bestFitter = fitter;
            return result;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

    }
}