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
 /*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.comdnmr.modelfree.models;

import java.util.List;

/**
 *
 * @author brucejohnson
 */
public class MFModelIso2s extends MFModelIso1s {

    double sf2;

    public MFModelIso2s(boolean fitTau, double targetTau, double tauFraction,
            boolean includeEx) {
        super(fitTau, targetTau, tauFraction, includeEx);
        nPars = includeEx ? 4 : 3;
    }

    public MFModelIso2s(double targetTau) {
        this(false, targetTau, 0.0, false);
    }

    public MFModelIso2s() {
        this(true, 0.0, 0.0, false);
    }

    @Override
    public List<String> getParNames() {
        return getAllParNames("Sf2", "Tau_s", "Ss2");
    }

    @Override
    public double[] calc(double[] omegas) {
        double tauMx = tauM * 1.0e-9;
        double tauSx = tauS * 1.0e-9;
        double[] J = new double[omegas.length];
        int j = 0;
        double s2 = sf2 * ss2;
        for (double omega : omegas) {
            double omega2 = omega * omega;
            double taus = tauMx * tauSx / (tauMx + tauSx);
            double value1 = s2 * tauMx / (1.0 + omega2 * tauMx * tauMx);
            double value2 = (sf2 - s2) * (taus) / (1.0 + omega2 * taus * taus);
            J[j++] = 0.4 * (value1 + value2);
        }
        return J;
    }

    @Override
    public double[] calc(double[] omegas, double[] pars) {
        int parStart = 0;
        if (fitTau) {
            tauM = pars[0];
            parStart = 1;
        }

        this.sf2 = pars[parStart];
        this.tauS = pars[parStart + 1];
        this.ss2 = pars[parStart + 2];
        return calc(omegas);
    }

    public double[] calc(double[] omegas, double s2, double tauS, double sf2) {
        this.sf2 = s2;
        this.tauS = tauS;
        this.ss2 = sf2;
        return calc(omegas);
    }

    @Override
    public boolean checkParConstraints() {
        return tauS < tauM;
    }

    @Override
    public double[] getStart() {
        if (includeEx) {
            return getParValues(targetTau, 0.9, targetTau / 5.0, 0.9, 2.0);
        } else {
            return getParValues(targetTau, 0.9, targetTau / 5.0, 0.9);
        }
    }

    @Override
    public double[] getLower() {
        if (includeEx) {
            return getParValues(tauLower(), 0.0, SLOW_LIMIT, 0.0, 0.0);
        } else {
            return getParValues(tauLower(), 0.0, SLOW_LIMIT, 0.0);
        }
    }

    @Override
    public double[] getUpper() {
        if (includeEx) {
            return getParValues(tauUpper(), 1.0, targetTau / 2.0, 1.0, 100.0);
        } else {
            return getParValues(tauUpper(), 1.0, targetTau / 2.0, 1.0);

        }
    }

    @Override
    public int getNumber() {
        return 5;
    }

    @Override
    public String getName() {
        return "model2s";
    }

}
