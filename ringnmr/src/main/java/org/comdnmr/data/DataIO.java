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
package org.comdnmr.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.comdnmr.eqnfit.CESTEquation;
import org.comdnmr.eqnfit.CESTFitter;
import org.comdnmr.eqnfit.CPMGEquation;
import org.comdnmr.eqnfit.CPMGFitter;
import org.comdnmr.eqnfit.CurveFit;
import org.comdnmr.eqnfit.EquationType;
import org.comdnmr.eqnfit.ExpEquation;
import org.comdnmr.eqnfit.ExpFitter;
import org.comdnmr.eqnfit.PlotEquation;
import org.comdnmr.eqnfit.R1RhoEquation;
import org.comdnmr.eqnfit.R1RhoFitter;
import org.nmrfx.chemistry.Atom;
import org.nmrfx.chemistry.Entity;
import org.nmrfx.chemistry.InvalidMoleculeException;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.chemistry.Polymer;
import org.nmrfx.chemistry.Residue;
import org.nmrfx.chemistry.io.MMcifReader;
import org.nmrfx.chemistry.io.MoleculeIOException;
import org.nmrfx.chemistry.io.NMRNEFReader;
import org.nmrfx.chemistry.io.NMRStarReader;
import org.nmrfx.chemistry.io.NMRStarWriter;
import org.nmrfx.chemistry.io.PDBFile;
import org.nmrfx.chemistry.relax.OrderPar;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.chemistry.relax.RelaxationData;
import org.nmrfx.chemistry.relax.RelaxationData.relaxTypes;
import org.nmrfx.chemistry.relax.RelaxationRex;
import org.nmrfx.chemistry.relax.RelaxationValues;
import org.nmrfx.chemistry.relax.ResonanceSource;
import org.nmrfx.chemistry.utilities.CSVRE;
import org.nmrfx.peaks.InvalidPeakException;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.star.ParseException;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Bruce Johnson
 */
public class DataIO {

    public enum XCONV {
        IDENTITY() {
        },
        TAU2() {
            @Override
            double convert(double value, double[] pars, Experiment expData) {
                return 1000.0 / (2.0 * value);
            }
        },
        PPMTOHZ() {
            @Override
            double convert(double value, double[] pars, Experiment expData) {
                return value * expData.getNucleusField();
            }
        },
        HZTOPPM() {
            @Override
            double convert(double value, double[] pars, Experiment expData) {
                return value / expData.getNucleusField();
            }
        },
        CALC() {
            @Override
            double convert(double value, double[] pars, Experiment expData) {
                return pars[0] + pars[2] * (value + pars[1]);
            }
        };

        double convert(double value, double[] pars, Experiment expData) {
            return value;
        }
    }

    public enum YCONV {
        IDENTITY() {
        },
        RATE() {
            @Override
            Double convert(double value, double refValue, double tau) {
                Double result = null;
                if ((value < refValue) && (value > 0.0)) {
                    result = -Math.log(value / refValue) / tau;

                }
                return result;
            }
        },
        NORMALIZE() {
            @Override
            Double convert(double value, double refValue, double tau) {
                return value / refValue;
            }
        };

        Double convert(double value, double refValue, double tau) {
            return value;
        }
    }

    static Pattern resPatter = Pattern.compile("[^0-9]*([0-9]+)[^0-9]*");

    private DataIO() {
    }

    public static void loadFromPeakList(PeakList peakList, Experiment expData,
            ExperimentSet resProp, String xConvStr, String yConvStr, DynamicsSource dynamicsSourceFactory) {
        loadFromPeakList(peakList, expData, resProp, XCONV.valueOf(xConvStr.toUpperCase()),
                YCONV.valueOf(yConvStr.toUpperCase()), dynamicsSourceFactory);

    }

    public static void loadFromPeakList(PeakList peakList, Experiment experiment,
            ExperimentSet experimentSet, XCONV xConv, YCONV yConv, DynamicsSource dynamicsSourceFactory) {
        String expMode = experiment.getExpMode();
        DatasetBase dataset = DatasetBase.getDataset(peakList.fileName);
        final double[] xValues;
        double[] measureX = peakList.getMeasureValues();
        if (measureX != null) {
            xValues = measureX;
        } else if (dataset != null) {
            xValues = dataset.getValues(2);
        } else {
            throw new IllegalArgumentException("Peaklist or dataset doesn't have measured values");
        }
        experimentSet.addExperimentData(experiment.getName(), experiment);
        boolean eSet = false;
        if (expMode.equals("noe")) {
            eSet = true;
        }
        if (peakList.peaks().stream().filter(p -> p.getMeasures().isEmpty()).findAny().isPresent()) {
            throw new IllegalArgumentException("Some peaks don't have measured values");
        }
        peakList.peaks().stream().filter(peak -> peak.getStatus() >= 0).forEach(peak -> {
            List<Double> xValueList = new ArrayList<>();
            List<Double> yValueList = new ArrayList<>();
            List<Double> errValueList = new ArrayList<>();

            if (peak.getMeasures().isPresent()) {
                double[][] v = peak.getMeasures().get();
                int offset = 0;
                double refIntensity = 1.0;
                double refNoise = 0.0;
                if (expMode.equals("noe")) {
                    offset = 1;
                    refIntensity = v[0][0];
                    refNoise = v[1][0];
                }
                if (expMode.equals("noe") && (xValues.length > 3)) {
                    double noes[] = new double[xValues.length / 2];
                    for (int i = 0; i < noes.length; i++) {
                        if (xValues[i * 2] > 0.5) {
                            noes[i] = v[0][i * 2] / v[0][i * 2 + 1];
                        } else {
                            noes[i] = v[0][i * 2 + 1] / v[0][i * 2];
                        }
                    }
                    DescriptiveStatistics dStat = new DescriptiveStatistics(noes);
                    xValueList.add(1.0);
                    yValueList.add(dStat.getMean());
                    errValueList.add(dStat.getStandardDeviation());

                } else {
                    for (int i = offset; i < v[0].length; i++) {
                        xValueList.add(xValues[i]);
                        double expIntensity = v[0][i];
                        yValueList.add(expIntensity / refIntensity);
                        if (expMode.equals("noe")) {
                            double expNoise = v[1][i];
                            double r1 = refNoise / refIntensity;
                            double r2 = expNoise / expIntensity;
                            double eValue = Math.abs(expIntensity / refIntensity) * Math.sqrt(r1 * r1 + r2 * r2);
                            errValueList.add(eValue);
                        } else {
                            errValueList.add(0.0);
                        }
                    }
                }
            }
            Optional<ResonanceSource> resSourceOpt = dynamicsSourceFactory.createFromPeak(peak);
            if (resSourceOpt.isPresent()) {
                ResonanceSource resSource = resSourceOpt.get();
                if (expMode.equals("cest")) {
                    processCESTData((CESTExperiment) experiment, resSource, xValueList, yValueList, errValueList);
                } else {
                    ExperimentData residueData = new ExperimentData(experiment, resSource, xValueList, yValueList, errValueList);
                    experiment.addResidueData(resSource, residueData);
                }

                ExperimentResult residueInfo = experimentSet.getExperimentResult(resSource);

                if (residueInfo == null) {
                    residueInfo = new ExperimentResult(experimentSet, resSource, 0, 0, 0);
                    experimentSet.addExperimentResult(resSource, residueInfo);
                }
                if (expMode.equals("noe")) {
                    residueInfo.value = yValueList.get(0);
                    residueInfo.err = errValueList.get(0);
                }
            }
        });
        if (!eSet) {
            double errValue = estimateErrors(experiment);
            setErrors(experiment, errValue);
        }

    }

    public static void loadPeakFile(String fileName, Experiment experiment,
            ExperimentSet experimientSet, XCONV xConv, YCONV yConv,
            HashMap<String, Object> errorPars, double[] delayCalc,
            DynamicsSource dynamicsSourceFactory)
            throws IOException, IllegalArgumentException { //(String fileName, ExperimentSet resProp, String nucleus,
//            double temperature, double B0field, double tau, double[] vcpmgs, String expMode,
//            HashMap<String, Object> errorPars, double[] delayCalc) throws IOException, IllegalArgumentException {
        System.out.println("load peak file");
//        Path path = Paths.get(fileName);
//        String fileTail = path.getFileName().toString();
//        fileTail = fileTail.substring(0, fileTail.indexOf('.'));

//        ExperimentData expData = new ExperimentData(fileTail, nucleus, B0field, temperature, tau, xvals, expMode, errorPars, delayCalc, B1field);
//        String fileName = expData.getName();
        Path path = Paths.get(fileName);
        if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new FileNotFoundException(fileName);
        }
        String expMode = experiment.getExpMode();
        double[] xVals = null;
        double tau = 0.0;
        if (experiment instanceof OffsetExperiment) {
            tau = ((OffsetExperiment) experiment).getTau();
        }
        if (experiment instanceof DoubleArrayExperiment) {
            xVals = ((DoubleArrayExperiment) experiment).getXVals();
        }
        experimientSet.addExperimentData(experiment.getName(), experiment);
        boolean eSet = false;
        double errF = 0.05;
        double noise = 1.0;
        String errorMode = "";
        if (errorPars != null) {
            if (errorPars.containsKey("mode")) {
                errorMode = errorPars.get("mode").toString();
                if (errorMode.equals("percent")) {
                    if (errorPars.containsKey("value")) {
                        String percentValue = errorPars.get("value").toString();
                        errF = Double.parseDouble(percentValue) * 0.01;
                        eSet = true;
                    }
                } else if (errorMode.equals("noise")) {
                    if (errorPars.containsKey("value")) {
                        String noiseValueStr = errorPars.get("value").toString();
                        noise = Double.parseDouble(noiseValueStr);
                        eSet = true;
                    }
                }
            }
        }

        boolean gotHeader = false;
        boolean hasErrColumns = false;
        String[] peakRefs;
        double[] xValues = null;
        int offset = 0;
        int residueField = -1;
        int peakField = -1;
        //  Peak       Residue N       R1      R2      T11     T3      T4      T9      T5      T10     T12     T6      T7      T8
        int fakeRes = 1;
        Map<String, List<Integer>> xValIndices = new HashMap<>();
        List<List<Integer>> repIndices = new ArrayList<>();
        try (BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.charAt(0) == '#') {
                    continue;
                }
                String[] sfields = line.split("\t", -1);
                if (!gotHeader) {
                    int nfields = sfields.length;
                    if (fileName.endsWith(".mpk2")) {
                        // find last B0field that starts with "lab"
                        //   .mpk2 files have peak labels in columns like "lab1", "lab2"
                        //   intensities start in next column

                        for (int i = nfields - 1; i >= 0; i--) {
                            if (sfields[i].startsWith("lab")) {
                                offset = i + 1;
                                break;
                            }
                        }
                        // find first lab1,lab2... B0field to get residue number from
                        for (int i = 0; i < nfields; i++) {
                            if (sfields[i].startsWith("lab")) {
                                residueField = i;
                                break;
                            }
                        }
                        // find id label to get peak number
                        for (int i = 0; i < nfields; i++) {
                            if (sfields[i].startsWith("id")) {
                                peakField = i;
                                break;
                            }
                        }
                        // check to see if file has err values
                        for (int i = 0; i < nfields; i++) {
                            if (sfields[i].startsWith("err")) {
                                hasErrColumns = true;
                                break;
                            }
                        }
                        // find indices of x values, including any replicates
                        for (int i = 0; i < nfields; i++) {
                            if (!expMode.equals("noe") && Character.isDigit(sfields[i].charAt(0))
                                    || (expMode.equals("noe") && Character.isDigit(sfields[i].charAt(0)) && sfields[i].contains("1"))) {
                                xValIndices.computeIfAbsent(sfields[i], s -> new ArrayList<>()).add(i);
                            }
                        }
                        for (List<Integer> indices : xValIndices.values()) {
                            if (indices.size() > 1) {
                                repIndices.add(indices);
                                break;
                            }
                        }
                    } else {
                        for (int i = 0; i < nfields; i++) {
                            if (sfields[i].startsWith("Res")) {
                                residueField = i;
                                offset = i + 1;
                                break;
                            }
                        }
                    }
                    if (expMode.equals("cest") || expMode.equals("cpmg") || expMode.equals("noe")) {
                        offset++;
                        if (hasErrColumns) {
                            offset++;
                        }
                    }
                    int nValues = nfields - offset;
                    if (hasErrColumns) {
                        nValues /= 2;
                    }
                    xValues = new double[nValues];
                    peakRefs = new String[nValues];
                    System.out.println("off " + offset + " " + nfields + " " + hasErrColumns);
                    int iX = 0;
                    for (int i = offset; i < nfields; i++) {
                        // fixme assumes first vcpmg is the 0 ref   
                        // fixme. need to explicitly account for alternating x-value, errorHeader fields
                        if (xVals == null) {
                            try {
                                double x = Double.parseDouble(sfields[i].trim());
                                if (hasErrColumns) {
                                    i++;
                                }
                                xValues[iX] = xConv.convert(x, delayCalc, experiment);

                            } catch (NumberFormatException nFE) {
                            }
                        } else {
                            xValues[iX] = xVals[iX];
                        }
                        peakRefs[iX] = String.valueOf(iX);
//                        peakRefList.add(peakRefs[iX]);
                        iX++;
                    }
                    gotHeader = true;
                    if (experiment instanceof DoubleArrayExperiment) {
                        ((DoubleArrayExperiment) experiment).setXVals(xValues);
                    }
                } else {
                    String residueNum = "";
                    int peakNum = -1;
                    if (residueField != -1) {
                        residueNum = sfields[residueField].trim();
                    }
                    if (peakField != -1) {
                        peakNum = (int) Double.parseDouble(sfields[peakField].trim());
                    }
                    if (residueNum.equals("")) {
                        residueNum = String.valueOf(fakeRes);
                    } else if (residueNum.indexOf('.') != -1) {
                        int dotIndex = residueNum.indexOf('.');
                        residueNum = residueNum.substring(0, dotIndex);
                    }
                    double refIntensity = 1.0;
                    double refError = 0.0;
                    double avgIntensity = 1.0;
                    double avgRefIntensity = 1.0;
                    if (expMode.equals("cest") || expMode.equals("cpmg") || expMode.equals("noe")) {
                        int refOffset = offset - 1;
                        if (hasErrColumns) {
                            refOffset = offset - 2;
                            refError = Double.parseDouble(sfields[offset - 1].trim());
                        }
                        refIntensity = Double.parseDouble(sfields[refOffset].trim());
                        for (List<Integer> list : repIndices) {
                            if (list.contains(refOffset)) {
                                double sum = 0.0;
                                double refSum = 0.0;
                                for (int index : list) {
                                    sum += Double.parseDouble(sfields[index].trim());
                                    refSum += Double.parseDouble(sfields[refOffset].trim());
                                }
                                avgIntensity = sum / list.size();
                                avgRefIntensity = refSum / list.size();
                            }
                        }

                    }
                    List<Double> xValueList = new ArrayList<>();
                    List<Double> yValueList = new ArrayList<>();
                    List<Double> errValueList = new ArrayList<>();
                    boolean ok = true;
                    int iX = 0;
                    for (int i = offset; i < sfields.length; i++) {
                        String valueStr = sfields[i].trim();
                        if ((valueStr.length() == 0) || valueStr.equals("NA")) {
                            continue;
                        }
                        Double yValue;
                        double intensity;
                        try {
                            intensity = Double.parseDouble(sfields[i].trim());
                            yValue = yConv.convert(intensity, refIntensity, tau);
                        } catch (NumberFormatException nFE) {
                            ok = false;
                            continue;
                        }
                        if ((yValue == null) || Double.isNaN(yValue) || Double.isInfinite(yValue)) {
                            ok = false;
                            continue;
                        }
                        xValueList.add(xValues[iX]);
                        yValueList.add(yValue);

                        double eValue = 0.0;
                        if (hasErrColumns) {
                            i++;
                        }
                        double diffSum = 0.0;
                        double refDiffSum = 0.0;
                        int nReps = 0;
                        for (List<Integer> list : repIndices) {
                            if (list.contains(i)) {
                                nReps = list.size();
                                for (int index : list) {
                                    diffSum += Double.parseDouble(sfields[index].trim()) - avgIntensity;
                                    refDiffSum += Double.parseDouble(sfields[index - 1].trim()) - avgRefIntensity;
                                }
                            }
                        }
                        double normIntensity = diffSum / (nReps - 1);
                        double normRefIntensity = refDiffSum / (nReps - 1);
                        if (hasErrColumns && errorMode.equals("measured")) {
                            eValue = Double.parseDouble(sfields[i].trim());
                            eSet = true;
                            if (expMode.equals("cpmg")) {
                                if (normIntensity != 0) {
                                    eValue = eValue / (normIntensity * tau);
                                } else {
                                    eValue = eValue / (intensity * tau);
                                }
                            } else if (expMode.equals("noe") && (yConv == YCONV.NORMALIZE)) {
                                double r1 = refError / refIntensity;
                                double r2 = eValue / intensity;
                                if (normIntensity != 0 && normRefIntensity != 0) {
                                    r1 = refError / normRefIntensity;
                                    r2 = eValue / normIntensity;
                                }
                                eValue = Math.abs(yValue) * Math.sqrt(r1 * r1 + r2 * r2);
                            }
                        } else if (expMode.equals("cpmg")) {
                            if (errorMode.equals("noise")) {
                                if (normIntensity != 0) {
                                    eValue = noise / (normIntensity * tau);
                                } else {
                                    eValue = noise / (intensity * tau);
                                }
                            } else if (errorMode.equals("percent")) {
                                if (normIntensity != 0 && normRefIntensity != 0) {
                                    eValue = (errF * normRefIntensity) / (normIntensity * tau);
                                } else {
                                    eValue = (errF * refIntensity) / (intensity * tau);
                                }
                            }
                        } else if (expMode.equals("noe") && (yConv == YCONV.NORMALIZE)) {
                            double r1 = noise / refIntensity;
                            double r2 = noise / intensity;
                            if (normIntensity != 0 && normRefIntensity != 0) {
                                r1 = noise / normRefIntensity;
                                r2 = noise / normIntensity;
                            }
                            eValue = Math.abs(yValue) * Math.sqrt(r1 * r1 + r2 * r2);
                        } else {
                            if (errorMode.equals("percent")) {
                                eValue = Math.abs(yValue) * errF;
                            } else if (errorMode.equals("noise")) {
                                eValue = noise;
                            }
                        }
                        errValueList.add(eValue);
                        iX++;
                    }
                    if (!ok) {
                        continue;
                    }
                    Optional<ResonanceSource> resSourceOpt = dynamicsSourceFactory.createFromSpecifiers(expMode + "." + peakNum, residueNum, "H", "N");
                    if (!resSourceOpt.isPresent()) {
                        throw new IllegalArgumentException("Can't generate resonance source from peak " + expMode + "." + peakNum);
                    }
                    ResonanceSource dynSource = resSourceOpt.get();
                    if (expMode.equals("cest")) {
                        processCESTData((CESTExperiment) experiment, dynSource, xValueList, yValueList, errValueList);
                    } else {
                        ExperimentData residueData = new ExperimentData(experiment, dynSource, xValueList, yValueList, errValueList);
                        experiment.addResidueData(dynSource, residueData);
                    }
                    ExperimentResult residueInfo = experimientSet.getExperimentResult(dynSource);
                    // DynamicsSource dynSource = expData.getSource();

                    if (residueInfo == null) {
                        residueInfo = new ExperimentResult(experimientSet, dynSource, 0, 0, 0);
                        experimientSet.addExperimentResult(dynSource, residueInfo);
                    }
                    if (expMode.equals("noe")) {
                        residueInfo.value = yValueList.get(0);
                        residueInfo.err = errValueList.get(0);
                    }

                    fakeRes++;
                }
            }
        }
        if (!eSet) {
            double errValue = estimateErrors(experiment);
            setErrors(experiment, errValue);
        }
    }

    public static void processCESTData(OffsetExperiment expData, ResonanceSource dynSource,
            List<Double> xValueList, List<Double> yValueList, List<Double> errValueList) {
        Double B1field = expData.getB1Field();
        List<Double> B1fieldList = new ArrayList<>();
        xValueList.forEach((_item) -> {
            B1fieldList.add(B1field);
        });
        double tau = expData.getTau();
        List<Double> tauList = new ArrayList<>();
        xValueList.forEach((_item) -> {
            tauList.add(tau);
        });
        List<Double> bFieldUniqueValue = new ArrayList<>();
        bFieldUniqueValue.add(B1fieldList.get(0));
        List<Double> tauList1 = new ArrayList<>();
        tauList1.add(tauList.get(0));
        List<Double>[] xValueLists = new ArrayList[3];
        xValueLists[0] = xValueList;
        xValueLists[1] = B1fieldList;
        xValueLists[2] = tauList;

        ExperimentData residueData = new ExperimentData(expData, dynSource, xValueLists, yValueList, errValueList);
        expData.addResidueData(dynSource, residueData);
        expData.getExtras().clear();
        expData.setExtras(bFieldUniqueValue);
        expData.setExtras(tauList1);

    }

    /**
     * Get the residue name from a molecule, if present.
     *
     * @param residueNum int. The residue number.
     * @return name String. The name of the corresponding residue in the
     * molecule.
     */
    public static String getResidueName(int residueNum) {
        MoleculeBase mol = MoleculeFactory.getActive();
        String name = "";
        if (mol != null) {
            Iterator entityIterator = mol.entityLabels.values().iterator();
            while (entityIterator.hasNext()) {
                Entity entity = (Entity) entityIterator.next();
                if (entity instanceof Polymer) {
                    List<Residue> resList = ((Polymer) entity).getResidues();
                    for (Residue res : resList) {
                        int num = res.getIDNum();
                        if (num == residueNum) {
                            name = res.getName();
                            break;
                        }
                    }
                }
            }
        }
        return name;
    }

    public static void loadResidueDataFile(String fileName, Experiment expData,
            String residueNum, String atomName, ExperimentSet experimentSet, String nucleus,
            double temperature, double field,
            HashMap<String, Object> errorPars, XCONV xConv, YCONV yConv,
            double refIntensity, DynamicsSource dynamicsSourceFactory)
            throws IOException, IllegalArgumentException {
        boolean gotHeader = false;
        int nValues = 0;
        List<Double> xValueList = new ArrayList<>();
        List<Double> yValueList = new ArrayList<>();
        List<Double> errValueList = new ArrayList<>();
        System.out.println("Load XY file " + fileName + " for res " + residueNum + " atom " + atomName);

        experimentSet.addExperimentData(expData.getName(), expData);
        String splitPattern = "\t";
        String[] sfields;

        try (BufferedReader fileReader = new BufferedReader(new FileReader(fileName))) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }

//            String line = fileReader.readLine();
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.charAt(0) == '#') {
                    continue;
                }
                if (!gotHeader) {
                    if (!line.contains("\t")) {
                        splitPattern = ",";
                        if (!line.contains(",")) {
                            splitPattern = " +";
                        }
                    }
                    sfields = line.split(splitPattern, -1);
                    nValues = sfields.length;
                    gotHeader = true;
                } else {
                    sfields = line.split(splitPattern, -1);
                    try {

                        double offsetFreq = Double.parseDouble(sfields[0].trim());
                        double intensity = Double.parseDouble(sfields[1].trim());
                        double error = 0.01;
                        if (nValues > 2) {
                            error = Double.parseDouble(sfields[2].trim());
                        }
                        offsetFreq = xConv.convert(offsetFreq, null, expData);
                        intensity = yConv.convert(intensity, refIntensity, 0.0);
                        xValueList.add(offsetFreq);
                        yValueList.add(intensity);
                        errValueList.add(error);
                    } catch (NumberFormatException nFE) {
                        System.out.println(nFE.getMessage());
//                        continue;
                    }
                }
            }
        } catch (IOException ex) {
            System.out.println(ex.getMessage());

            Logger.getLogger(DataIO.class.getName()).log(Level.SEVERE, null, ex);
        }
        Optional<ResonanceSource> resSourceOpt = dynamicsSourceFactory.createFromSpecifiers(expData.getExpMode() + "." + 0, residueNum, atomName);
        if (!resSourceOpt.isPresent()) {
            throw new IllegalArgumentException("Can't generate resonance source from data " + expData.getName() + "." + 0);
        }
        ResonanceSource resSource = resSourceOpt.get();

        processCESTData((OffsetExperiment) expData, resSource, xValueList, yValueList, errValueList);
        ExperimentResult expResult = experimentSet.getExperimentResult(resSource);
        if (expResult == null) {
            expResult = new ExperimentResult(experimentSet, resSource, 0, 0, 0);
            experimentSet.addExperimentResult(resSource, expResult);
        }

    }

    public static void loadTextFile(Experiment expData, String fileName, ExperimentSet experimentSet,
            String nucleus, double temperature, double field, XCONV xConv,
            String expMode, DynamicsSource dynamicsSourceFactory)
            throws IOException, IllegalArgumentException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.indexOf('.'));

//        ExperimentData expData = new ExperimentData(fileTail, nucleus, B0field, temperature);
        experimentSet.addExperimentData(fileTail, expData);
        boolean gotHeader = false;
        String[] peakRefs;
        double[][] xValues = null;
//        List<Double> xValues = new ArrayList<>();
        int peakNum = 0;
        try (BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
//            String line = fileReader.readLine();
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                String[] sfields = line.split("\t", -1);
                if (!gotHeader) {
                    int j = 0;
                    int nValues = (sfields.length - 1) / 2;
                    xValues = new double[1][nValues];
                    peakRefs = new String[nValues];
                    for (int i = 1; i < sfields.length - 1; i += 2) {
                        double xValue = Double.parseDouble(sfields[i].trim());
                        xValues[0][j] = xConv.convert(xValue, null, expData);
                        peakRefs[j] = String.valueOf(j);
                        j++;
                    }
                    gotHeader = true;
                } else {
                    int nValues = (sfields.length - 1) / 2;
                    String residueNum = sfields[0].trim();
                    double[] yValues = new double[nValues];
                    double[] errValues = new double[nValues];
                    int j = 0;
                    for (int i = 1; i < sfields.length - 1; i += 2) {
                        double r2Eff = Double.parseDouble(sfields[i].trim());
                        double r2EffErr = Double.parseDouble(sfields[i + 1].trim());
                        yValues[j] = r2Eff;
                        errValues[j] = r2EffErr;
                        j++;
                    }

                    Optional<ResonanceSource> resSourceOpt = dynamicsSourceFactory.createFromSpecifiers(expMode + "." + peakNum, residueNum, "H", "N");
                    if (!resSourceOpt.isPresent()) {
                        throw new IllegalArgumentException("Can't generate resonance source from peak " + expMode + "." + peakNum);
                    }
                    ResonanceSource dynSource = resSourceOpt.get();
                    ExperimentData residueData = new ExperimentData(expData, dynSource, xValues, yValues, errValues);
                    expData.addResidueData(dynSource, residueData);

                    ExperimentResult residueInfo = experimentSet.getExperimentResult(dynSource);
                    if (residueInfo == null) {
                        residueInfo = new ExperimentResult(experimentSet, dynSource, 0, 0, 0);
                        experimentSet.addExperimentResult(dynSource, residueInfo);
                    }
                    peakNum++;
                }
            }
        }
    }

    public static void setPercentileErrors(Experiment expData, double fraction) {
        expData.experimentalDataSets.values().forEach((residueData) -> {
            double[] yValues = residueData.getYValues();
            for (int i = 0; i < yValues.length; i++) {
                residueData.setErrValue(i, yValues[i] * fraction);
            }
        });
    }

    public static void setErrors(Experiment expData, double error) {
        for (ExperimentData residueData : expData.experimentalDataSets.values()) {
            double[] errValues = residueData.getErrValues();
            Arrays.fill(errValues, error);
        }

    }

    public static double estimateErrors(Experiment expData) {
        int nDups = 0;
        double sumDelta2 = 0.0;
        double sumAbs = 0.0;
        for (ExperimentData residueData : expData.experimentalDataSets.values()) {
            double[][] xValues = residueData.getXValues();
            double[] yValues = residueData.getYValues();
            for (int i = 0; i < yValues.length - 1; i++) {
                for (int j = (i + 1); j < yValues.length; j++) {
                    if (xValues[0][i] == xValues[0][j]) {
                        double delta = yValues[i] - yValues[j];
                        sumDelta2 += delta * delta;
                        sumAbs += Math.abs(delta);
                        nDups++;
                    }

                }
            }
        }
        double errorA = 0.0;
        double error2 = 0.0;
        if (nDups > 0) {
            error2 = Math.sqrt(sumDelta2 / (2.0 * nDups));
            errorA = Math.sqrt(Math.PI / 2.0) * sumAbs / (2.0 * nDups);
        }
        System.out.println("data " + expData.name + " errors " + error2 + "errorA " + errorA + " ndup " + nDups);
        return error2;
    }

    public static ExperimentSet loadResultsFile(String fitMode, String fileName,
            DynamicsSource dynamicsSourceFactory) throws IOException {
        Path path = Paths.get(fileName);
        String fileTail = path.getFileName().toString();
        fileTail = fileTail.substring(0, fileTail.indexOf('.'));
        ExperimentSet experimentSet = new ExperimentSet(fileTail, fileName);
        File file = new File(fileName);
        if (!file.exists()) {
            return experimentSet;
        }

        /*
Residue	 Peak	GrpSz	Group	Equation	   RMS	   AIC	Best	     R2	  R2.sd	    Rex	 Rex.sd	    Kex	 Kex.sd	     pA	  pA.sd	     dPPM	  dPPM.sd
36	    26	    1	    0	    NOEX	  1.28	 49.95		   9.22	   0.09								
36	    26	    1	    0	CPMGFAST	  0.25	  7.44	best	   8.88	   0.09	   2.94	   0.08	 259.33	  17.97				
36	    26	    1	    0	CPMGSLOW	  0.28	 14.05		   8.89	   0.09			 164.36	  53.17	   0.96	   0.14	  24.51	   6.76
        
         */
        String[] header = null;
        HashMap<String, Integer> headerMap = new HashMap<>();
        try (BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
//            String line = fileReader.readLine();
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.startsWith("#")) {
                    continue;
                }
                if (header == null) {
                    header = line.split("\t");
                    for (int i = 0; i < header.length; i++) {
                        headerMap.put(header[i].trim(), i);
                    }
                } else {

                    String[] sfields = line.split("\t", -1);
                    String[] stdHeaders = {"Best", "Residue", "Equation", "RMS", "GrpSz", "Group", "Peak", "State"};
                    for (String stdHeader : stdHeaders) {
                        int index = headerMap.get(stdHeader);
                        if (index == -1) {
                            throw new IOException("Missing header " + stdHeader);
                        }
                    }
                    String bestValue = sfields[headerMap.get("Best")];
                    String residueNumber = sfields[headerMap.get("Residue")].trim();
                    String equationName = sfields[headerMap.get("Equation")].trim().toUpperCase();
                    String groupSzStr = sfields[headerMap.get("GrpSz")].trim();
                    int groupSize = Integer.parseInt(groupSzStr);
                    String groupIdStr = sfields[headerMap.get("Group")].trim();
                    int groupId = Integer.parseInt(groupIdStr);
                    String peakIdStr = sfields[headerMap.get("Peak")].trim();
                    int peakNum = Integer.parseInt(peakIdStr);
                    String stateString = sfields[headerMap.get("State")].trim();
                    Optional<ResonanceSource> resSourceOpt = dynamicsSourceFactory.createFromAtomSpecifiers(fileTail + "." + peakNum, residueNumber + ".H");
                    if (!resSourceOpt.isPresent()) {
                        throw new IllegalArgumentException("Can't generate resonance source from peak " + fileTail + "." + peakNum);
                    }
                    ResonanceSource dynSource = resSourceOpt.get();
                    ExperimentResult expResult = experimentSet.getExperimentResult(dynSource);

                    if (expResult == null) {
                        expResult = new ExperimentResult(experimentSet, dynSource, groupId, groupSize, peakNum);
                        experimentSet.addExperimentResult(dynSource, expResult);
                    }
                    double[] fields = new double[1];
                    fields[0] = 1.0;
                    int parStart = headerMap.get("Best") + 1;
                    HashMap<String, Double> parMap = new HashMap<>();
                    EquationType equationType;
                    List<String> equationNames;
                    switch (fitMode) {
                        case "exp":
                            equationType = ExpEquation.valueOf(equationName);
                            equationNames = ExpFitter.getEquationNames();
                            break;
                        case "cest":
                            equationName = equationName.replace("CEST", "");
                            if (equationName.startsWith("R1RHO")) {
                                equationName = equationName.replace("R1RHO", "");
                                switch (equationName) {
                                    case "EXACT1":
                                        equationName = "EIGENEXACT1";
                                        break;
                                    case "N":
                                        equationName = "LAGUERRE";
                                        break;
                                    case "PERTURBATION":
                                        equationName = "TROTT_PALMER";
                                        break;
                                    case "PERTURBATIONNOEX":
                                        equationName = "NOEX";
                                        break;
                                    default:
                                        break;
                                }
                            }
                            equationType = CESTEquation.valueOf(equationName);
                            equationNames = CESTFitter.getEquationNames();
                            break;
                        case "r1rho":
                            equationName = equationName.replace("R1RHO", "");
                            if (equationName.equals("PERTURBATION")) {
                                equationName = "TROTT_PALMER";
                            } else if (equationName.equals("PERTURBATIONNOEX")) {
                                equationName = "NOEX";
                            }
                            equationType = R1RhoEquation.valueOf(equationName);
                            equationNames = R1RhoFitter.getEquationNames();
                            break;
                        default:
                            equationType = CPMGEquation.valueOf(equationName);
                            equationNames = CPMGFitter.getEquationNames();
                            break;
                    }
                    String[] eqnParNames = equationType.getParNames();
                    int nPar = eqnParNames.length;

                    double[] pars = new double[nPar];
                    double[] errs = new double[nPar];
                    int iPar = 0;
                    for (String parName : eqnParNames) {
                        int index = headerMap.get(parName);
                        pars[iPar] = Double.parseDouble(sfields[index].trim());
                        index = headerMap.get(parName + ".sd");
                        errs[iPar++] = Double.parseDouble(sfields[index].trim());
                    }
                    //parMap.put("Kex", 0.0); // Set Kex to 0.0.  Overwritten below if exists in output file

                    for (int i = parStart; i < header.length; i++) {
                        if (sfields[i].trim().length() > 0) {
                            String parName = header[i].trim();
                            double parValue = Double.parseDouble(sfields[i].trim());
                            parMap.put(parName, parValue);
                        }
                    }
                    String[] extraPars = {"AIC", "RMS"};
                    for (String extraPar : extraPars) {
                        int parIndex = headerMap.get(extraPar);
                        if (parIndex != -1) {
                            parMap.put(extraPar, Double.parseDouble(sfields[parIndex].trim()));
                        }
                    }
                    parMap.put("Equation", 1.0 + equationNames.indexOf(equationName));
                    PlotEquation plotEquation = new PlotEquation(fitMode, equationName, pars, errs, fields);
                    CurveFit curveSet = new CurveFit(stateString, dynSource, parMap, plotEquation);
                    expResult.addCurveSet(curveSet, bestValue.equals("best"));
                }
            }

        }
        return experimentSet;
    }

    static void getFitParameters(ExperimentSet resProp, Map<String, Object> dataMap2) {
        HashMap<String, Object> fitParMap = (HashMap<String, Object>) dataMap2.get("parameters");
        if (fitParMap != null) {
            Boolean absValueMode = (Boolean) fitParMap.get("absValue");
            if (absValueMode != null) {
                resProp.setAbsValueMode(absValueMode);
            }
            String bootStrapMode = (String) fitParMap.get("bootStrap");
            if (bootStrapMode != null) {
                resProp.setBootStrapMode(bootStrapMode);
            }

            System.out.println("absmode " + absValueMode + " bootstrap " + bootStrapMode);
        }
    }

    static void processYAMLDataSections(ExperimentSet resProp, Map<String, Object> dataMap2, Path dirPath, String expMode) throws IOException {

        ArrayList<HashMap<String, Object>> dataList = (ArrayList<HashMap<String, Object>>) dataMap2.get("data");
        DataIO.processYAMLDataSections(resProp, dirPath, expMode, dataList);
    }

    static Map<String, List<Double>> getConstraints(Map... maps) {

        Map<String, List<Double>> constraintMap = null;
        for (Map map : maps) {
            if (map.containsKey("constraints")) {
                constraintMap = (Map<String, List<Double>>) map.get("constraints");
                if (constraintMap != null) {
                    System.out.println(constraintMap.toString());
                }
            }
        }
        return constraintMap;
    }

    static Double getDoubleValue(Map<String, Object> dataMap, String key, Double defaultValue) {
        Double value = defaultValue;
        if (dataMap.containsKey(key)) {
            Object oValue = dataMap.get(key);
            if (oValue instanceof Number) {
                value = ((Number) oValue).doubleValue();
            }
        }
        return value;
    }

    public static ExperimentSet processPeakList(PeakList peakList,
            DynamicsSource dynamicsSourceFactory) {
        ExperimentSet experimentSet = null;
        if (peakList != null) {
            String peakListName = peakList.getName();
            List<Number> vcpmgList = null;
            DatasetBase dataset = DatasetBase.getDataset(peakList.fileName);
            String expMode = "exp";
            experimentSet = new ExperimentSet(peakListName, peakListName);
            expMode = expMode.toLowerCase();
            experimentSet.setExpMode(expMode);
            if (vcpmgList == null) {
                String nucleus = dataset.getNucleus(1).getName();
                double B0field = dataset.getSf(1);
                double temperature = dataset.getTempK();
                Double tau = null;
                Double B1field = null;
                Experiment expData = null;
                String expName = peakListName;
                switch (expMode) {
                    case "r1":
                        expData = new T1Experiment(experimentSet, expName, nucleus, B0field, temperature);
                        break;
                    case "r2":
                        expData = new T2Experiment(experimentSet, expName, nucleus, B0field, temperature);
                        break;
                    case "cest":
                        expData = new CESTExperiment(experimentSet, expName, nucleus, B0field, temperature, tau, B1field);
                        break;
                    case "r1rho":
                        expData = new R1rhoOffsetExperiment(experimentSet, expName, nucleus, B0field, temperature, tau, B1field);
                        break;
                    case "cpmg":
                        expData = new CPMGExperiment(experimentSet, expName, nucleus, B0field, temperature);
                        if (vcpmgList != null) {
                            double[] vcpmgs = new double[vcpmgList.size()];
                            for (int i = 0; i < vcpmgs.length; i++) {
                                vcpmgs[i] = vcpmgList.get(i).doubleValue();
                            }
                            CPMGExperiment cpmgExp = (CPMGExperiment) expData;
                            cpmgExp.setXVals(vcpmgs);
                        }
                        break;
                    case "noe":
                        expData = new NOEExperiment(experimentSet, expName, nucleus, B0field, temperature);
                        break;
                    default:
                }

                loadFromPeakList(peakList, expData, experimentSet,
                        XCONV.IDENTITY, YCONV.IDENTITY, dynamicsSourceFactory);
            }
        }
        return experimentSet;

    }

    public static void processYAMLDataSections(ExperimentSet experimentSet, Path dirPath, String expMode, ArrayList<HashMap<String, Object>> dataList) throws IOException {
        for (HashMap<String, Object> dataMap3 : dataList) {
            Double temperature = getDoubleValue(dataMap3, "temperature", null);
            if (temperature != null) {
                temperature += 273.15;
            } else {
                temperature = getDoubleValue(dataMap3, "temperatureK", null);
            }

            XCONV xConv;
            YCONV yConv;
            switch (expMode) {
                case "cpmg":
                    xConv = XCONV.TAU2;
                    yConv = YCONV.RATE;
                    break;
                case "cest":
                    xConv = XCONV.IDENTITY;
                    yConv = YCONV.NORMALIZE;
                    break;
                default:
                    xConv = XCONV.IDENTITY;
                    yConv = YCONV.IDENTITY;
            }
            if (dataMap3.containsKey("xconv")) {
                String xConvStr = dataMap3.get("xconv").toString();
                xConv = XCONV.valueOf(xConvStr.toUpperCase());
                if (xConv == null) {
                    throw new IOException("Bad xconversion type");
                }
            }
            if (dataMap3.containsKey("yconv")) {
                String xConvStr = dataMap3.get("yconv").toString();
                yConv = YCONV.valueOf(xConvStr.toUpperCase());
                if (yConv == null) {
                    throw new IOException("Bad yconversion type");
                }
            }

            Double B0field = ((Number) dataMap3.get("B0")).doubleValue();
            String nucleus = (String) dataMap3.get("nucleus");
            List<Number> vcpmgList = (List<Number>) dataMap3.get("vcpmg");
            Double tau = getDoubleValue(dataMap3, "tau", 1.0);
            Double B1field = getDoubleValue(dataMap3, "B1", null);

            String fileFormat = (String) dataMap3.get("format");

            HashMap<String, Object> errorPars = (HashMap<String, Object>) dataMap3.get("error");
            Object delayField = dataMap3.get("delays");
            System.out.println("delays " + delayField);
            double[] delayCalc = {0.0, 0.0, 1.0};
            if (delayField instanceof Map) {
                Map<String, Object> delayMap = (Map<String, Object>) delayField;
                delayCalc[0] = getDoubleValue(delayMap, "delta0", 0.0);
                delayCalc[1] = getDoubleValue(delayMap, "c0", 0.0);
                delayCalc[2] = getDoubleValue(delayMap, "delta", 1.0);
            }
            System.out.println("err " + errorPars);
            String dataFileName = (String) dataMap3.get("file");
            File dataFile = null;
            String fileTail = null;
            if (dataFileName != null) {
                dataFile = new File(dataFileName);
                fileTail = dataFile.getName();
                int dot = fileTail.lastIndexOf(".");
                if (dot != -1) {
                    fileTail = fileTail.substring(0, dot);
                }
            }
            String expName = (String) dataMap3.get("name");
            if ((expName == null) && (fileTail != null)) {
                expName = fileTail;
            }

            if (expName == null) {
                throw new IOException("No file or name entry in yaml file");
            }

            Experiment expData;
            switch (expMode) {
                case "r1":
                    expData = new T1Experiment(experimentSet, expName, nucleus, B0field, temperature);
                    break;
                case "r2":
                    expData = new T2Experiment(experimentSet, expName, nucleus, B0field, temperature);
                    break;
                case "cest":
                    expData = new CESTExperiment(experimentSet, expName, nucleus, B0field, temperature, tau, B1field);
                    break;
                case "r1rho":
                    expData = new R1rhoOffsetExperiment(experimentSet, expName, nucleus, B0field, temperature, tau, B1field);
                    break;
                case "cpmg":
                    expData = new CPMGExperiment(experimentSet, expName, nucleus, B0field, temperature);
                    if (vcpmgList != null) {
                        double[] vcpmgs = new double[vcpmgList.size()];
                        for (int i = 0; i < vcpmgs.length; i++) {
                            vcpmgs[i] = vcpmgList.get(i).doubleValue();
                        }
                        CPMGExperiment cpmgExp = (CPMGExperiment) expData;
                        cpmgExp.setXVals(vcpmgs);
                    }
                    break;
                case "noe":
                    expData = new NOEExperiment(experimentSet, expName, nucleus, B0field, temperature);
                    break;
                default:
                    throw new IOException("Invalid expMode in .yaml file " + expMode);
            }
            DynamicsSource dynamicsSourceFactory = new DynamicsSource(true, true, true, true);
            if ((fileFormat != null) && fileFormat.equals("mpk2")) {
                if (dataFile == null) {
                    throw new IllegalArgumentException("No file par in .yaml file");
                }
                if (!dataFile.isAbsolute()) {
                    dataFileName = dirPath.resolve(dataFileName).toString();
                }
                loadPeakFile(dataFileName, expData, experimentSet, xConv, yConv,
                        errorPars, delayCalc, dynamicsSourceFactory);

            } else if ((fileFormat != null) && fileFormat.equals("ires")) {
                List<Map<String, Object>> filesMaps = (List<Map<String, Object>>) dataMap3.get("files");
                for (Map<String, Object> filesMap : filesMaps) {
                    Map<String, List<Double>> constraintMap = getConstraints(dataMap3, filesMap);
                    expData.setConstraints(constraintMap);
                    if (!filesMap.containsKey("residue")) {
                        throw new IOException("No residue key in ires section");
                    }
                    String residueNum = filesMap.get("residue").toString();
                    String atomName = "H";
                    if (filesMap.containsKey("atom")) {
                        atomName = filesMap.get("atom").toString();
                    }

                    String dataFileName2 = (String) filesMap.get("file");
                    double refIntensity = 1.0;
                    if (filesMap.containsKey("refIntensity")) {
                        refIntensity = ((Number) filesMap.get("refIntensity")).doubleValue();
                    }
                    File file = new File(dataFileName2).getAbsoluteFile();
                    dataFileName2 = file.getName();
                    String textFileName = FileSystems.getDefault().getPath(dirPath.toString(), dataFileName2).toString();
                    loadResidueDataFile(textFileName, expData, residueNum, atomName, experimentSet, nucleus,
                            temperature, B0field, errorPars, xConv, yConv,
                            refIntensity, dynamicsSourceFactory);
                }
            } else if (vcpmgList == null) {
                File file = new File(dataFileName).getAbsoluteFile();
                dataFileName = file.getName();
                String textFileName = FileSystems.getDefault().getPath(dirPath.toString(), dataFileName).toString();
                loadTextFile(expData, textFileName, experimentSet, nucleus,
                        temperature, B0field, xConv, expMode, dynamicsSourceFactory);
            } else {
                double[] vcpmgs = new double[vcpmgList.size()];
                for (int i = 0; i < vcpmgs.length; i++) {
                    vcpmgs[i] = vcpmgList.get(i).doubleValue();
                }
                loadPeakFile(dataFileName, expData, experimentSet, xConv, yConv,
                        errorPars, delayCalc, dynamicsSourceFactory);
            }
        }

    }

    public static ExperimentSet loadYAMLFile(String fileName) throws FileNotFoundException, IOException {
        File yamlFile = new File(fileName).getAbsoluteFile();
        ExperimentSet resProp = null;
        try (InputStream input = new FileInputStream(yamlFile)) {
            Path path = yamlFile.toPath();
            Path dirPath = path.getParent();

            Yaml yaml = new Yaml();
            DynamicsSource dynamicsSourceFactory = new DynamicsSource(true, true, true, true);
            for (Object data : yaml.loadAll(input)) {
                Map dataMap = (HashMap<String, Object>) data;
                Map dataMap2 = (HashMap<String, Object>) dataMap.get("fit");
                if (dataMap2 != null) {
                    System.out.println(dataMap2.toString());
                    String expMode = (String) dataMap2.get("mode");
                    if (expMode == null) {
                        throw new IOException("No mode value in .yaml file");
                    }
                    if (expMode.equalsIgnoreCase("t1")) {
                        expMode = "r1";
                    } else if (expMode.equalsIgnoreCase("t2")) {
                        expMode = "r2";
                    }
                    String parName = (String) dataMap2.get("file");
                    if (parName == null) {
                        String yamlName = yamlFile.getName();
                        parName = yamlName.substring(0, yamlName.length() - 5) + "_out.txt";
                    }
                    String parFileName = FileSystems.getDefault().getPath(dirPath.toString(), parName).toString();
                    resProp = DataIO.loadResultsFile(expMode, parFileName, dynamicsSourceFactory);

                    resProp.setExpMode(expMode);
                    getFitParameters(resProp, dataMap2);
                    processYAMLDataSections(resProp, dataMap2, dirPath, expMode);
                }
            }
        }
        return resProp;

    }

    public static void saveResultsFile(String fileName, ExperimentSet resProp, boolean saveStats) {
        String[] headerFields = {"Residue", "Peak", "GrpSz", "Group", "State", "Equation", "RMS", "AIC", "Best"};
        String[] headerFields2 = {"Residue", "Peak", "GrpSz", "Group", "State", "RefineOpt", "RefineTime",
            "BootstrapOpt", "BootstrapTime", "Samples", "AbsMode", "NonParametricMode", "StartRadius", "FinalRadius",
            "Tolerance", "Weight", "Equation", "RMS", "AIC", "Best"};
        if (saveStats) {
            headerFields = headerFields2;
        }
        StringBuilder headerBuilder = new StringBuilder();
        String[] cpmgFields = {"R2", "dPPMmin", "Kex", "pA", "dPPM"};
        String[] expFields = {"A", "R"};
        String[] cestFields = {"Kex", "Pb", "deltaA0", "deltaB0", "R1A", "R1B", "R2A", "R2B"};
        String[] parFields;
        switch (resProp.getExpMode()) {
            case "cpmg":
                parFields = cpmgFields;
                break;
            case "cest":
            case "r1rho":
                parFields = cestFields;
                break;
            default:
                parFields = expFields;
                break;
        }

        for (String field : headerFields) {
            if (headerBuilder.length() > 0) {
                headerBuilder.append('\t');
            }
            headerBuilder.append(field);
        }
        for (String field : parFields) {
            headerBuilder.append('\t').append(field).append('\t').append(field).append(".sd");
        }

        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(headerBuilder.toString());
            resProp.getExperimentResults().stream().
                    sorted((a, b) -> Integer.compare(a.getAtom().getIndex(), b.getAtom().getIndex())).
                    forEach(resInfo -> {
                        String outputLine = resInfo.toOutputString(parFields, saveStats);
                        try {
                            writer.write(outputLine);
                        } catch (IOException ex) {
                            Logger.getLogger(DataIO.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    });
        } catch (IOException ex) {
            Logger.getLogger(DataIO.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    public static List<Double>[] loadSimDataFile(File file) throws IOException, IllegalArgumentException {
        Path path = file.toPath();
        List<Double> xValues = new ArrayList<>();
        List<Double> yValues = new ArrayList<>();
        List<Double> errValues = new ArrayList<>();
        try (BufferedReader fileReader = Files.newBufferedReader(path)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }

                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.contains("#")) {
                    continue;
                }
                String[] sfields = line.split("\t", -1);

                xValues.add(Double.parseDouble(sfields[0]));
                yValues.add(Double.parseDouble(sfields[1]));
                if (sfields.length > 2) {
                    errValues.add(Double.parseDouble(sfields[2]));
                }
            }
        }
        List[] result = {xValues, yValues, errValues};
        return result;
    }

    /**
     * Load molecular information from a file.
     *
     * @param fileName String. The file to read.
     * @param type String. The filetype: pdb, star, nef, or cif.
     * @throws ParseException
     * @throws MoleculeIOException
     * @throws IOException
     */
    public static void readMoleculeFile(String fileName, String type) throws ParseException, MoleculeIOException, IOException {
        switch (type) {
            case "pdb":
                PDBFile pdb = new PDBFile();
                MoleculeBase mol = pdb.read(fileName);
                mol.updateAtomArray();
                break;
            case "star":
                File starFile = new File(fileName).getAbsoluteFile();
                NMRStarReader.read(starFile);
                break;
            case "nef":
                NMRNEFReader.read(fileName);
                break;
            case "cif":
                File cifFile = new File(fileName).getAbsoluteFile();
                MMcifReader.read(cifFile);
                break;
            default:
                break;
        }
        System.out.println("loaded molecule " + MoleculeFactory.getActive().getName() + " from " + fileName);
    }

    /**
     * Add the R1/R2 fit results to a map, and to a molecule, if present.
     *
     * @param expSet ExperimentSet. The set pf experimental results.
     * @param expType relaxTypes. The experiment type, R1 or R2.
     */
    public static void addRelaxationFitResults(ExperimentSet expSet, relaxTypes expType) {
        String datasetName = expSet.getName() + "_RING_fit";
        String expName = "EXPAB";
        double[] fields = expSet.getFields();

        MoleculeBase mol = MoleculeFactory.getActive();
        if (mol != null) {
            double temperature = 25.0;
            for (int f = 0; f < fields.length; f++) {
                int field = (int) fields[f];
                final int iList = f;
                List<ExperimentResult> expResults = expSet.getExperimentResults();
                Collections.sort(expResults, (a, b) -> Integer.compare(a.getAtom().getIndex(), b.getAtom().getIndex()));
                expResults.forEach((expResult) -> {
                    Double value;
                    Double error;
                    if (expType == relaxTypes.NOE) {
                        value = expResult.value;
                        error = expResult.err;
                    } else {
                        final Map<String, CurveFit> parMap = expResult.curveSets.get(expName);
                        final Object[] states = parMap.keySet().toArray();
                        if (expResults.indexOf(expResult) == 0) {
                            Arrays.sort(states, (a, b) -> a.toString().compareTo(b.toString()));
                        }
                        CurveFit curveFit = parMap.get(states[iList].toString());
                        Map<String, Double> fitPars = curveFit.getParMap();
                        value = fitPars.get("R");
                        error = fitPars.get("R.sd");
                    }

                    ResonanceSource dynSource = expResult.getSource();
                    Atom[] atoms = dynSource.getAtoms();
                    for (Atom atom : atoms) {
                        Map<String, String> extras = new HashMap<>();
                        String coherenceType = "Sz";
                        String units = "s-1";
                        extras.put("coherenceType", coherenceType);
                        extras.put("units", units);
                        switch (expType) {
                            case R1: {
                                RelaxationData relaxData = new RelaxationData(datasetName, expType, dynSource, field, temperature, value, error, extras);
                                atom.getRelaxationData().put(datasetName, relaxData);
                                break;
                            }
                            case NOE: {
                                RelaxationData relaxData = new RelaxationData(datasetName, expType, dynSource, field, temperature, value, error, extras);
                                atom.getRelaxationData().put(datasetName, relaxData);
                                break;
                            }
                            default: {
                                RelaxationRex relaxData = new RelaxationRex(datasetName, expType, dynSource, field, temperature, value, error, null, null, extras);
                                atom.getRelaxationData().put(datasetName, relaxData);
                                break;
                            }
                        }
                    }
                });
            }
            System.out.println(expType + " fit results added to molecule " + mol.getName());
        }
    }

    public static void writeSTAR3File(String fileName) throws IOException, ParseException, InvalidMoleculeException, InvalidPeakException {
        try (FileWriter writer = new FileWriter(fileName)) {
            writeSTAR3File(writer);
        }
    }

    public static void writeSTAR3File(File file) throws IOException, ParseException, InvalidMoleculeException, InvalidPeakException {
        try (FileWriter writer = new FileWriter(file)) {
            writeSTAR3File(writer);
        }
    }

    public static void writeSTAR3File(FileWriter chan) throws IOException, ParseException, InvalidMoleculeException, InvalidPeakException {
        NMRStarWriter.writeAll(chan);
    }

    public static Map<String, RelaxSet> getDataFromMolecule() {
        Map<String, RelaxSet> relaxValueMap = new HashMap<>();

        MoleculeBase mol = MoleculeFactory.getActive();
        if (mol != null) {
            String molName = mol.getName();
            for (Atom atom : mol.getAtomArray()) {
                Map<String, RelaxationValues> valuesMap = new HashMap<>();
                Map<String, RelaxationData> relaxData = atom.getRelaxationData();
                Map<String, OrderPar> orderData = atom.getOrderPars();
                valuesMap.putAll(relaxData);
                valuesMap.putAll(orderData);
                if (!valuesMap.isEmpty()) {
                    for (Map.Entry<String, RelaxationValues> entry : valuesMap.entrySet()) {
                        RelaxationValues relaxValue = entry.getValue();
                        String entryName = entry.getKey();
                        String expType = relaxValue.getName();
                        String relaxSetName = molName + "_" + entryName + "_" + expType;
                        RelaxSet relaxSet;

                        if (!relaxValueMap.containsKey(relaxSetName)) {
                            relaxSet = new RelaxSet(relaxSetName);
                            relaxValueMap.put(relaxSetName, relaxSet);
                        } else {
                            relaxSet = relaxValueMap.get(relaxSetName);
                        }
                        Entity entity = atom.getEntity();
                        if (entity instanceof Residue) {
                            relaxSet.addValue(relaxValue);
                        }
                    }
                }
            }
        }
        return relaxValueMap;
    }

    public static void loadRelaxationTextFile(File file) throws IOException, IllegalArgumentException {
        Path path = file.toPath();
        String[] types = {"R1", "R2", "NOE"};
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex != -1) {
            fileName = fileName.substring(0, dotIndex);
        }

        try (BufferedReader fileReader = Files.newBufferedReader(path)) {
            Optional<String> sepStr = Optional.empty();
            int iField = -1;
            int iRes = -1;
            Map<String, Integer> fieldMap = new HashMap<>();
            List<String> header;
            DynamicsSource dynamicsSourceFactory = new DynamicsSource(true, true, true, true);
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                line = line.strip();
                if (sepStr.isEmpty()) {
                    if (line.contains("\t")) {
                        sepStr = Optional.of("\t");
                    } else if (line.contains(",")) {
                        sepStr = Optional.of(",");
                    } else {
                        sepStr = Optional.of(" ");
                    }
                    if (line.startsWith("#")) {
                        line = line.substring(1);

                    }
                    line = line.strip();
                    String[] fields = CSVRE.parseLine(sepStr.get(), line);

                    header = new ArrayList<>();
                    for (var field : fields) {
                        header.add(field.toUpperCase());
                    }
                    iField = header.indexOf("FIELD");
                    iRes = header.indexOf("RESIDUE");
                    if (iRes == -1) {
                        iRes = header.indexOf("RESI");
                    }
                    if (iRes == -1) {
                        iRes = 0;
                    }
                    for (var type : types) {
                        fieldMap.put(type, header.indexOf(type));
                    }
                } else {
                    String[] fields = CSVRE.parseLine(sepStr.get(), line);
                    String residue = fields[iRes];
                    double field = Double.parseDouble(fields[iField]);
                    for (var type : types) {
                        int index = fieldMap.get(type);
                        double value = Double.parseDouble(fields[index]);
                        double error = Double.parseDouble(fields[index + 1]);
                        String id = fileName + "_" + type + "_" + Math.round(field);
                        Optional<ResonanceSource> resSourceOpt = dynamicsSourceFactory.
                                createFromSpecifiers(fileName + "." + residue, residue, "N", "H");

                        if (!resSourceOpt.isPresent()) {
                            throw new IllegalArgumentException("Can't generate resonance source from peak " + fileName + "." + residue);
                        }
                        ResonanceSource dynSource = resSourceOpt.get();

                        RelaxationData.add(id, type, dynSource, field, value, error);
                    }
                }
            }
        }
    }
}
