package org.comdnmr.modelfree;

import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.rng.sampling.distribution.DirichletSampler;
import org.comdnmr.modelfree.models.MFModelIso;
import org.comdnmr.modelfree.models.MFModelIso2sf;
import org.nmrfx.chemistry.*;
import org.nmrfx.chemistry.relax.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FitDeuteriumModel extends FitModel {
    Map<String, MolDataValues> molData = null;

    public void setData(Map<String, MolDataValues> molData) {
        this.molData = molData;
    }

    public static Map<String, MolDataValues> getData(boolean requireCoords) {
        Map<String, MolDataValues> molDataValues = new HashMap<>();
        MoleculeBase mol = MoleculeFactory.getActive();
        if (mol != null) {
            for (Polymer polymer : mol.getPolymers()) {
                for (Residue residue : polymer.getResidues()) {
                    for (Atom atom : residue.getAtoms()) {
                        MolDataValues molData = getMolDataValues(atom);
                        if ((molData != null) && !molData.dataValues.isEmpty()) {
                            molDataValues.put(molData.specifier, molData);
                        }
                    }
                }
            }
        }
        return molDataValues;
    }

    public static boolean hasDeuteriumData(Atom atom) {
        var relaxData = atom.getRelaxationData();
        boolean hasDeuterium = relaxData.values().stream().
                anyMatch(v -> v.getRelaxationSet().relaxType() == RelaxTypes.RQ);
        return hasDeuterium;
    }

    public static MolDataValues getMolDataValues(Atom atom) {
        var relaxData = atom.getRelaxationData();
        boolean hasDeuterium = relaxData.values().stream().
                anyMatch(v -> v.getRelaxationSet().relaxType() == RelaxTypes.RQ);
        MolDataValues molData = null;
        if (hasDeuterium) {
            Vector3D vec = null;
            molData = vec == null
                    ? new MolDataValues(atom)
                    : new MolDataValues(atom, vec.toArray());
            Set<Long> fields = new HashSet<>();
            for (var entry : relaxData.entrySet()) {
                RelaxationSet relaxationSet = entry.getKey();
                if (relaxationSet.active()) {
                    fields.add(Math.round(relaxationSet.field()));
                }
            }
            for (var field : fields) {
                Double r1 = null;
                Double r1Error = null;

                Double r2 = null;
                Double r2Error = null;

                Double rQ = null;
                Double rQError = null;

                Double rAP = null;
                Double rAPError = null;

                for (var entry : relaxData.entrySet()) {
                    RelaxationData data = entry.getValue();
                    RelaxationSet relaxationSet = entry.getKey();
                    if (!relaxationSet.active()) {
                        continue;
                    }
                    if (Math.round(relaxationSet.field()) == field) {
                        switch (relaxationSet.relaxType()) {
                            case R1:
                                r1 = data.getValue();
                                r1Error = data.getError();
                                break;
                            case R2:
                                r2 = data.getValue();
                                r2Error = data.getError();
                                break;
                            case RQ:
                                rQ = data.getValue();
                                rQError = data.getError();
                                break;
                            case RAP:
                                rAP = data.getValue();
                                rAPError = data.getError();
                                break;
                        }
                    }
                }
                if ((r1 != null) && (r2 != null) && (rQ != null) && (rAP != null)) {
                    RelaxEquations relaxObj = RelaxEquations.getRelaxEquations(field * 1e6, "D", "C");

                    RelaxDataValue dValue = new DeuteriumDataValue(molData, r1, r1Error, r2, r2Error,
                            rQ, rQError, rAP, rAPError, relaxObj);
                    molData.addData(dValue);
                }
            }
        }
        return molData;
    }

    public Map<String, ModelFitResult> testIsoModel() {
        if ((molData == null) || (molData.isEmpty())) {
            molData = getData(false);
        }
        if (searchKey != null) {
            if (molData.containsKey(searchKey)) {
                var keepVal = molData.get(searchKey);
                molData.clear();
                molData.put(searchKey, keepVal);
            }
        }

        if (!molData.isEmpty()) {
            Map<String, ModelFitResult> results = testModels(molData, modelNames);
            return results;
        } else {
            throw new IllegalStateException("No relaxation data to analyze.  Need T1,T2 and NOE");
        }
    }

    public void calcJ(Map<String, MolDataValues> molData) {
        Map<String, MolDataValues> molDataRes = new TreeMap<>();
        for (String key : molData.keySet()) {
            molDataRes.clear();
            MolDataValues resData = molData.get(key);
            if (!resData.getData().isEmpty()) {
                molDataRes.put(key, resData);
                List<Double> rValues = new ArrayList<>();
                List<Double> errValues = new ArrayList<>();
                List<Double> fields = new ArrayList<>();
                for (var value : resData.dataValues) {
                    var dValue = (DeuteriumDataValue) value;
                    rValues.add(dValue.R1);
                    rValues.add(dValue.R2);
                    rValues.add(dValue.rQ);
                    rValues.add(dValue.rAP);
                    errValues.add(dValue.R1err);
                    errValues.add(dValue.R2err);
                    errValues.add(dValue.rQError);
                    errValues.add(dValue.rAPError);
                    fields.add(dValue.relaxObj.getSF());
                }
                double[][] mappingResult = DeuteriumMapping.jointMapping(rValues, errValues, fields);
                for (int i = 0; i < mappingResult[0].length; i++) {
                    double field = mappingResult[0][i];
                    double jValue = mappingResult[1][i];
                    double errValue = mappingResult[2][i];
                    double logJ = Math.log10(jValue);
                    double logJUp = Math.log10(jValue + errValue);
                    double logJLow = Math.log10(jValue - errValue);
                }

            }
        }
    }

    public Map<String, ModelFitResult> testModels(Map<String, MolDataValues> molData, List<String> modelNames) {
        Random random = new Random();
        AtomicInteger counts = new AtomicInteger();
        int n = molData.entrySet().size();
        Map<String, ModelFitResult> results = new ConcurrentHashMap<>();
        MoleculeBase moleculeBase = MoleculeFactory.getActive();
        Map<String, OrderParSet> orderParSetMap = moleculeBase.orderParSetMap();
        for (var modelName : modelNames) {
            OrderParSet orderParSet = orderParSetMap.computeIfAbsent("order_parameter_list_" + modelName, k -> new OrderParSet(k));
        }
        OrderParSet orderParSet = orderParSetMap.computeIfAbsent("order_parameter_list_1", k -> new OrderParSet(k));

        molData.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey)).parallel().forEach(e -> {
            updateProgress((double) counts.getAndIncrement() / n);
            if (cancelled.get()) {
                return;
            }
            MolDataValues resData = e.getValue();
            String key = e.getKey();
            if (!resData.getData().isEmpty()) {
                if (bootstrapMode != BootstrapMode.PARAMETRIC) {
                    Optional<ModelFitResult> result = testModelsWithBootstrapAggregation(orderParSetMap, resData, key, modelNames, random);
                    result.ifPresent(o -> results.put(key, o));
                } else {
                    Optional<ModelFitResult> result = testModels(orderParSetMap, resData, key, modelNames, random);
                    result.ifPresent(o -> results.put(key, o));
                }
            }
        });
        return results;
    }

    public Optional<ModelFitResult> testModels(Map<String, OrderParSet> orderParSetMap, MolDataValues resData, String key, List<String> modelNames, Random random) {
        Optional<ModelFitResult> result = Optional.empty();
        Map<String, MolDataValues> molDataRes = new TreeMap<>();
        molDataRes.put(key, resData);
        SpectralDensity spectralDensity = new SpectralDensity(key, resData.getJValues());
        Atom atom = resData.atom;
        ResonanceSource resSource = new ResonanceSource(resData.atom);

        MFModelIso bestModel = null;
        Score bestScore = null;
        double lowestAIC = Double.MAX_VALUE;
        boolean localFitTau;
        double localTauFraction;
        if (fitTau(molDataRes)) {
            localFitTau = true;
            localTauFraction = tauFraction;
        } else {
            localTauFraction = 0.0;
            localFitTau = false;
        }
        for (var modelName : modelNames) {
            MFModelIso model = MFModelIso.buildModel(modelName,
                    localFitTau, tau, localTauFraction, fitExchange);
            OrderParSet orderParSet = orderParSetMap.get("order_parameter_list_" + modelName);

            resData.setTestModel(model);
            Score score = tryModel(molDataRes, model, localTauFraction, localFitTau, random);
            if (score != null) {
                OrderPar orderPar = makeOrderPar(orderParSet, resSource, score, model, model.getParNames(), score.getPars(), null);
                atom.addOrderPar(orderParSet, orderPar);
                if (score.aicc() < lowestAIC) {
                    lowestAIC = score.aicc();
                    bestModel = model;
                    bestScore = score;
                }
            }
        }


        if (bestScore != null) {
            double[][] replicateData = null;
            if (nReplicates > 2) {
                replicateData = replicates(molDataRes, bestModel, localTauFraction, localFitTau, bestScore.getPars(), random);
            }
            OrderParSet orderParSet = orderParSetMap.get("order_parameter_list_1");
            OrderPar orderPar = makeOrderPar(orderParSet, resSource, bestScore, bestModel, bestModel.getParNames(), bestScore.getPars(), replicateData);
            atom.addOrderPar(orderParSet, orderPar);
            atom.addSpectralDensity(key, spectralDensity);
            ModelFitResult modelFitResult = new ModelFitResult(orderPar, replicateData, null);
            result = Optional.of(modelFitResult);
        }
        return result;
    }

    OrderPar makeOrderPar(OrderParSet orderParSet, ResonanceSource resSource, Score bestScore, MFModelIso bestModel,
                          List<String> parNames, double[] pars, double[][] replicateData) {
        OrderPar orderPar = new OrderPar(orderParSet, resSource, bestScore.rss, bestScore.nValues, bestScore.nPars, bestModel.getName());
        for (int iPar = 0; iPar < parNames.size(); iPar++) {
            String parName = parNames.get(iPar);
            double parValue = pars[iPar];
            Double parError = null;
            if (replicateData != null) {
                DescriptiveStatistics sumStat = new DescriptiveStatistics(replicateData[iPar]);
                parError = sumStat.getStandardDeviation();
            }
            orderPar = orderPar.set(parName, parValue, parError);
        }
        if (!bestModel.fitTau()) {
            orderPar = orderPar.set("Tau_e", bestModel.getTau(), 0.0);
        }

        orderPar = orderPar.set("model", (double) bestModel.getNumber(), null);
        if ((bestModel instanceof MFModelIso2sf) && useLambda) {
            orderPar = orderPar.setModel();
        }
        return orderPar;
    }

    public Optional<ModelFitResult> testModelsWithBootstrapAggregation(Map<String, OrderParSet> orderParSetMap, MolDataValues resData, String key, List<String> modelNames, Random random) {
        Optional<ModelFitResult> result = Optional.empty();
        Map<String, MolDataValues> molDataRes = new TreeMap<>();
        molDataRes.put(key, resData);

        boolean localFitTau;
        double localTauFraction;
        if (fitTau(molDataRes)) {
            localFitTau = true;
            localTauFraction = tauFraction;
        } else {
            localTauFraction = 0.0;
            localFitTau = false;
        }

        int maxPars = 5;
        double[][] jData = resData.getJValues();
        int nJ = jData[0].length;

        DirichletSampler dirichlet = null;
        dirichlet = DirichletSampler.symmetric(getRandomSource(), nJ, 4.0);

        double[][] replicateData = new double[maxPars][nReplicates];
        MFModelIso[] bestModels = new MFModelIso[nReplicates];
        Score[] bestScores = new Score[nReplicates];

        int[] totalCounts = new int[nJ];
        for (int iRep = 0; iRep < nReplicates; iRep++) {
            if (cancelled.get()) {
                return result;
            }
            double[] weights = dirichlet.sample();
            scaleWeights(weights);
            for (var molData : molDataRes.values()) {
                molData.weight(weights);
                molData.clearBootStrapSet();
            }
            List<Score> scores = new ArrayList<>();
            List<MFModelIso> models = new ArrayList<>();
            for (var modelName : modelNames) {
                MFModelIso model = MFModelIso.buildModel(modelName,
                        localFitTau, tau, localTauFraction, fitExchange);
                resData.setTestModel(model);
                Score score = tryModel(molDataRes, model, localTauFraction, localFitTau, random);
                scores.add(score);
                models.add(model);
            }
            int iBest = bestScore(scores);
            if (iBest != -1) {
                Score bestScore = scores.get(iBest);
                MFModelIso bestModel = models.get(iBest);
                double[] repPars = bestScore.getPars();
                bestModels[iRep] = bestModel;
                bestScores[iRep] = bestScore;
                double[] pars = bestModel.getStandardPars(repPars);

                for (int iPar = 0; iPar < pars.length; iPar++) {
                    replicateData[iPar][iRep] = pars[iPar];
                }
            }
        }
        for (int i = 0; i < totalCounts.length; i++) {
            totalCounts[i] /= nReplicates;
        }
        for (var molData : molDataRes.values()) {
            molData.clearBootStrapSet();
        }
        MFModelIso bestModel = MFModelIso.buildModel("D2sf",
                localFitTau, tau, localTauFraction, fitExchange);

        if (bestModels[0] != null) {
            String[] parNames = {"Tau_e", "Sf2", "Tau_f", "Ss2", "Tau_s"};

            ResonanceSource resSource = new ResonanceSource(resData.atom);
            double rssSum = 0.0;
            for (int i = 0; i < nReplicates; i++) {
                rssSum += bestScores[i].rss;
            }
            double rss = rssSum /= nReplicates;
            OrderParSet orderParSet = orderParSetMap.get("order_parameter_list_1");
            OrderPar orderPar = new OrderPar(orderParSet, resSource, rss, bestScores[0].nValues, parNames.length, bestModel.getName());
            double[][] cov = new double[nJ][parNames.length];
            double[] bestPars = new double[parNames.length];
            for (int iPar = 0; iPar < parNames.length; iPar++) {
                String parName = parNames[iPar];
                Double parError = null;
                DescriptiveStatistics sumStat = new DescriptiveStatistics(replicateData[iPar]);
                double parValue = useMedian ? sumStat.getPercentile(50.0) : sumStat.getMean();
                bestPars[iPar] = parValue;
                parError = sumStat.getStandardDeviation();
                orderPar = orderPar.set(parName, parValue, parError);
            }
            orderPar = orderPar.set("model", (double) bestModel.getNumber(), null);
            Atom atom = resData.atom;
            atom.addOrderPar(orderParSet, orderPar);
            double[][] jValues = resData.getJValues();
            SpectralDensity spectralDensity = new SpectralDensity(key, jValues);
            atom.addSpectralDensity(key, spectralDensity);
            resData.setTestModel(bestModel);
            Double validationScore = null;
            if (calcValidation) {
                validationScore = scoreBayesian(molDataRes, bestModel, bestPars, dirichlet,
                        nReplicates, nReplicates, localFitTau, localTauFraction);
            }
            ModelFitResult modelFitResult = new ModelFitResult(orderPar, replicateData, validationScore);
            result = Optional.of(modelFitResult);
        }
        return result;
    }

    Score tryModel(Map<String, MolDataValues> molDataRes, MFModelIso model, double localTauFraction, boolean localFitTau, Random random) {
        RelaxFit relaxFit = new RelaxFit();
        relaxFit.setRelaxData(molDataRes);
        relaxFit.setLambdaS(lambdaS);
        relaxFit.setLambdaTau(lambdaTau);
        relaxFit.setUseLambda(useLambda);
        relaxFit.setFitJ(true);
        model.setTauFraction(localTauFraction);
        double[] start = model.getStart();
        double[] lower = model.getLower();
        double[] upper = model.getUpper();
        double[] keepStart = start.clone();
        int nTries = 3;
        PointValuePair best = null;
        for (int i = 0; i < nTries; i++) {
            try {
                PointValuePair fitResult = relaxFit.fitResidueToModel(start, lower, upper);
                if ((i == 0) || (fitResult.getValue() < best.getValue())) {
                    best = fitResult;
                }
            } catch (Exception iaE) {
                System.out.println(iaE.getMessage());
            }
            for (int j = 0; j < start.length; j++) {
                start[j] = keepStart[j] + random.nextGaussian() * 0.1 * (upper[j] - lower[j]);
            }
        }
        if (best != null) {
            var score = relaxFit.score(best.getPoint(), true);
            return score;
        }
        return null;
    }

    double[][] replicates(Map<String, MolDataValues> molDataRes,
                          MFModelIso bestModel, double localTauFraction,
                          boolean localFitTau, double[] pars, Random random) {
        double[][] repData = new double[pars.length][nReplicates];
        for (int iRep = 0; iRep < nReplicates; iRep++) {
            boolean ok = false;
            for (int jTry = 0; jTry < 5; jTry++) {
                Score score2 = fitReplicate(molDataRes, bestModel, localTauFraction, localFitTau, pars, random);
                if (score2 != null) {
                    double[] repPars = score2.getPars();
                    for (int iPar = 0; iPar < pars.length; iPar++) {
                        repData[iPar][iRep] = repPars[iPar];
                    }
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return null;
            }
        }
        return repData;
    }

    Score fitReplicate(Map<String, MolDataValues> molDataRes, MFModelIso model,
                       double localTauFraction, boolean localFitTau, double[] pars, Random random) {
        RelaxFit relaxFit = new RelaxFit();
        relaxFit.setRelaxData(molDataRes);
        relaxFit.setLambdaS(lambdaS);
        relaxFit.setLambdaTau(lambdaTau);
        relaxFit.setUseLambda(useLambda);
        relaxFit.setFitJ(fitJ);
        Map<String, MolDataValues> molDataMap = relaxFit.genBootstrap(random, model, pars);
        relaxFit.setRelaxData(molDataMap);

        model.setTauFraction(localTauFraction);
        double[] lower = model.getLower();
        double[] upper = model.getUpper();
        PointValuePair fitResult = relaxFit.fitResidueToModel(pars, lower, upper);
        if (fitResult == null) {
            return null;
        } else {
            return relaxFit.score(fitResult.getPoint(), true);
        }
    }

}
