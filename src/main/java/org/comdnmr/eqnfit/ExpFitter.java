/*
 * CoMD/NMR Software : A Program for Analyzing NMR Dynamics Data
 * Copyright (C) 2018-2019 Bruce A Johnson
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.comdnmr.eqnfit;

import org.comdnmr.util.CoMDPreferences;
import org.comdnmr.data.ResidueProperties;
import org.comdnmr.data.ExperimentalData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.apache.commons.math3.optim.PointValuePair;
import org.comdnmr.data.Experiment;
import org.comdnmr.util.CoMDOptions;

/**
 *
 * @author Bruce Johnson
 */
public class ExpFitter implements EquationFitter {

    public static final double[] SIMX = {0.0, 0.025, 0.05, 0.1, 0.15, 0.25, 0.35, 0.5, 0.75, 1.0};

    FitFunction expModel;
    CoMDOptions options;
    List<Double> xValues = new ArrayList<>();
    List<Double> yValues = new ArrayList<>();
    List<Double> errValues = new ArrayList<>();
    List<Double> fieldValues = new ArrayList<>();
    List<Integer> idValues = new ArrayList<>();
    int nCurves = 1;
    int nResidues = 1;
    int[][] states;
    int[] stateCount;
    String[] resNums;
    static List<String> equationNameList = Arrays.asList(ExpEquation.getEquationNames());
    long errTime;
    static final String expType = "exp";

    class StateCount {

        int[][] states;
        int[] stateCount;

        StateCount(int[][] states, int[] stateCount) {
            this.states = states;
            this.stateCount = stateCount;
        }

        int getResIndex(int i) {
            return states[i][0];
        }

        int getTempIndex(int i) {
            return states[i][2];
        }
    }

    public ExpFitter(CoMDOptions options) {
        expModel = new ExpFitFunction(options);
        this.options = options;
    }

    public static int getMapIndex(int[] state, int[] stateCount, int... mask) {
        int index = 0;
//        System.out.println(state.length + " mask " + mask.length);
//        for (int i = 0; i < state.length; i++) {
//            System.out.print(" " + state[i]);
//        }
//        System.out.println("");
        double mult = 1.0;
        for (int i = 0; i < mask.length; i++) {
//            System.out.println("mask:" + mask[i] + " state[mask]:" + state[mask[i]] + " count:" + stateCount[mask[i]]);
            index += mult * state[mask[i]];
            mult *= stateCount[mask[i]];
        }
        return index;
    }

    @Override
    public void setData(List<Double>[] allXValues, List<Double> yValues, List<Double> errValues, List<Double> fieldValues) {
        xValues.clear();
        xValues.addAll(allXValues[0]);
        this.yValues.clear();
        this.yValues.addAll(yValues);
        this.errValues.clear();
        this.errValues.addAll(errValues);
        this.fieldValues.clear();
        this.fieldValues.addAll(fieldValues);
        this.idValues.clear();
        yValues.forEach((_item) -> {
            this.idValues.add(0);
        });
        resNums = new String[1];
        resNums[0] = "0";
        nCurves = 1;
        stateCount = new int[4];
        stateCount[0] = nResidues;
        stateCount[1] = 1;
        stateCount[2] = 1;
        stateCount[3] = 1;
        states = new int[1][4];
    }

    // public void setData(Collection<ExperimentData> expDataList, String[] resNums) {
    @Override
    public void setData(ResidueProperties resProps, String[] resNums) {
        this.resNums = resNums.clone();
        nResidues = resNums.length;
        int id = 0;
        resProps.setupMaps();
        stateCount = resProps.getStateCount(resNums.length);
        Collection<Experiment> expDataList = resProps.getExperimentData();
        nCurves = resNums.length * expDataList.size();
        states = new int[nCurves][];
        int k = 0;
        int resIndex = 0;
        for (String resNum : resNums) {
            for (Experiment expData : expDataList) {
                states[k++] = resProps.getStateIndices(resIndex, expData);
                ExperimentalData experimentalData = expData.getResidueData(resNum);
                //  need peakRefs
                double field = expData.getNucleusField();
                double[][] x = experimentalData.getXValues();
                double[] y = experimentalData.getYValues();
                double[] err = experimentalData.getErrValues();
                for (int i = 0; i < y.length; i++) {
                    xValues.add(x[0][i]);
                    yValues.add(y[i]);
                    errValues.add(err[i]);
                    fieldValues.add(field);
                    idValues.add(id);
                }
                id++;

            }
            resIndex++;
        }
    }

    @Override
    public FitFunction getFitModel() {
        return expModel;
    }

    @Override
    public List<String> getEquationNameList() {
        return getEquationNames();
    }
    
    @Override
    public String getExpType() {
        return expType;
    }

    public static List<String> getEquationNames() {
        List<String> activeEquations = CoMDPreferences.getActiveExpEquations();
        return activeEquations;
    }

    @Override
    public int[] getStateCount() {
        return stateCount;
    }

    @Override
    public int[][] getStates() {
        return states;
    }

    @Override
    public void setupFit(String eqn) {
        double[][] x = new double[1][yValues.size()];
        double[] y = new double[yValues.size()];
        double[] err = new double[yValues.size()];
        int[] idNums = new int[yValues.size()];
        double[] fields = new double[yValues.size()];
        for (int i = 0; i < x[0].length; i++) {
            x[0][i] = xValues.get(i);
            y[i] = yValues.get(i);
            err[i] = errValues.get(i);
            //System.out.println(x[0][i]+", "+x[0][i]+", "+x[0][i]+", "+x[0][i]);
            fields[i] = fieldValues.get(i);
            idNums[i] = idValues.get(i);
        }
        expModel.setEquation(eqn);
        expModel.setXY(x, y);
        expModel.setIds(idNums);
        expModel.setErr(err);
        expModel.setFieldValues(fields);
        expModel.setMap(stateCount, states);
    }

    @Override
    public List<ParValueInterface> guessPars(String eqn) {
        setupFit(eqn);
        double[] guesses = expModel.guess();
        String[] parNames = expModel.getParNames();
        int[][] map = expModel.getMap();
        List<ParValueInterface> parValues = new ArrayList<>();
        for (int i = 0; i < parNames.length; i++) {
            double guess = guesses[map[0][i]];
            ParValueInterface parValue = new ParValue(parNames[i], guess);
            parValues.add(parValue);
        }
        return parValues;
    }

    @Override
    public double rms(double[] pars) {
        double rms = expModel.getRMS(pars);
        return rms;
    }

    @Override
    public FitResult doFit(String eqn, double[] sliderguesses, CoMDOptions options) {
        setupFit(eqn);

        int[][] map = expModel.getMap();
        double[] guesses;
        if (sliderguesses != null) {
            //fixme
            guesses = sliderguesses;
        } else {
            guesses = expModel.guess();
        }
//        System.out.println("dofit guesses = " + guesses);
        double[][] boundaries = expModel.boundaries(guesses);
        double sigma = options.getStartRadius();
        PointValuePair result = expModel.refine(guesses, boundaries[0], boundaries[1],
                sigma, options.getOptimizer());
        double[] pars = result.getPoint();
        /*
        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[i].length; j++) {
                System.out.printf(" %3d", map[i][j]);
            }
            System.out.println("");
        }

        System.out.print("Fit pars ");
        for (int i = 0; i < pars.length; i++) {
            System.out.printf(" %.3f", pars[i]);
        }
        System.out.println("");
         */
        double aic = expModel.getAICc(pars);
        double rms = expModel.getRMS(pars);
        double rChiSq = expModel.getReducedChiSq(pars);

//        System.out.println("rms " + rms);
        int nGroupPars = expModel.getNGroupPars();
        sigma /= 2.0;

        String[] parNames = expModel.getParNames();
        double[] errEstimates;
        double[][] simPars = null;
        if (FitFunction.getCalcError()) {
            long startTime = System.currentTimeMillis();
            errEstimates = expModel.simBoundsStream(pars.clone(),
                    boundaries[0], boundaries[1], sigma, options);
            long endTime = System.currentTimeMillis();
            errTime = endTime - startTime;
            simPars = expModel.getSimPars();
        } else {
            errEstimates = new double[pars.length];
        }
        String refineOpt = options.getOptimizer();
        String bootstrapOpt = options.getBootStrapOptimizer();
        long fitTime = expModel.fitTime;
        long bootTime = errTime;
        int nSamples = options.getSampleSize();
        boolean useAbs = options.getAbsValueFit();
        boolean useNonParametric = options.getNonParametricBootstrap();
        double sRadius = options.getStartRadius();
        double fRadius = options.getFinalRadius();
        double tol = options.getTolerance();
        boolean useWeight = options.getWeightFit();
        CurveFit.CurveFitStats curveStats = new CurveFit.CurveFitStats(refineOpt, bootstrapOpt, fitTime, bootTime, nSamples, useAbs,
                useNonParametric, sRadius, fRadius, tol, useWeight);
        double[] usedFields = getFields(fieldValues, idValues);
        return getResults(this, eqn, parNames, resNums, map, states, usedFields, nGroupPars, pars, errEstimates, aic, rms, rChiSq, simPars, true, curveStats);
    }

    @Override
    public double[] getSimX(int nPts, double xLB, double xUB) {
        int nPoints = nPts;
        double[] x = new double[nPoints];
        double firstValue = xLB;
        double lastValue = xUB;
        double delta = (lastValue - firstValue) / (nPoints + 1);
        double value = firstValue;
        for (int i = 0; i < nPoints; i++) {
            x[i] = value;
            value += delta;

        }
        return x;
    }

    @Override
    public double[] getSimXDefaults() {
        return SIMX;
    }

}
