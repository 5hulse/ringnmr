package org.comdnmr.fit.calc;

import org.comdnmr.fit.calc.MtxExp;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrixFormat;
import org.apache.commons.math3.linear.RealMatrix;
import org.junit.Assert;
import org.junit.Test;

public class ExpMTest {

    private double[] refValues;
    private RealMatrix matrix;

    @Test
    public void testDiagonalMatrix() {
        RealMatrix matrix
                = MatrixUtils.createRealMatrix(new double[][]{
            {1.0, 0.0},
            {0.0, 2.0}
        });

        RealMatrix valid
                = MatrixUtils.createRealMatrix(new double[][]{
            {2.718281828459046, 0.0},
            {0.0, 7.389056098930650}
        });

        RealMatrixFormat formatter = new RealMatrixFormat(); 
        RealMatrix result = MtxExp.matrixExp(matrix);
        System.out.println(formatter.format(valid));
        System.out.println(formatter.format(result));

        double norm = result.subtract(valid).getNorm();
        Assert.assertEquals(0, norm, 6.0e-13);
    }
    
    @Test
    public void testDiagonalMatrix1() {
        RealMatrix matrix
                = MatrixUtils.createRealMatrix(new double[][]{
            {0.0, 6.0, 0.0, 0.0},
            {0.0, 0.0, 6.0, 0.0},
            {0.0, 0.0, 0.0, 6.0},
            {0.0, 0.0, 0.0, 0.0}
        });

        RealMatrix valid
                = MatrixUtils.createRealMatrix(new double[][]{
            {1.0, 6.0, 18.0, 36.0},
            {0.0, 1.0, 6.0, 18.0},
            {0.0, 0.0, 1.0, 6.0},
            {0.0, 0.0, 0.0, 1.0}
        });

        RealMatrixFormat formatter = new RealMatrixFormat(); 
        RealMatrix result = MtxExp.matrixExp(matrix);
        System.out.println(formatter.format(valid));
        System.out.println(formatter.format(result));

        double norm = result.subtract(valid).getNorm();
        Assert.assertEquals(0, norm, 6.0e-13);
    }
    
    @Test
    public void testDiagonalMatrix2() {
        RealMatrix matrix
                = MatrixUtils.createRealMatrix(new double[][]{
            {29.87942128909879,    0.7815750847907159, -2.289519314033932},
            {0.7815750847907159, 25.72656945571064,    8.680737820540137},
            {-2.289519314033932,   8.680737820540137,  34.39400925519054 }
        });

        RealMatrix valid
                = MatrixUtils.createRealMatrix(new double[][]{
            {5.496313853692378E+15, -1.823188097200898E+16, -3.047577080858001E+16},
            {-1.823188097200899E+16,  6.060522870222108E+16,  1.012918429302482E+17},
            {-3.047577080858001E+16,  1.012918429302482E+17,  1.692944112408493E+17}
        });

        RealMatrixFormat formatter = new RealMatrixFormat(); 
        RealMatrix result = MtxExp.matrixExp(matrix);
        System.out.println(formatter.format(valid));
        System.out.println(formatter.format(result));

        double norm = result.subtract(valid).getNorm();
        Assert.assertEquals(0, norm, 3.0e3);
    }
}