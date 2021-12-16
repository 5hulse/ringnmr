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
package org.comdnmr.gui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.comdnmr.data.DataIO;
import org.comdnmr.data.DynamicsSource;
import org.comdnmr.data.Experiment;
import org.comdnmr.data.ExperimentSet;
import org.comdnmr.data.T1Experiment;
import org.controlsfx.control.textfield.TextFields;
import org.controlsfx.dialog.ExceptionDialog;
import org.nmrfx.chart.DataSeries;
import org.nmrfx.chemistry.MoleculeBase;
import org.nmrfx.chemistry.MoleculeFactory;
import org.nmrfx.datasets.DatasetBase;
import org.nmrfx.peaks.PeakList;
import org.nmrfx.utils.GUIUtils;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author Martha Beckwith
 */
public class InputDataInterface {

    static final String INACTIVE_TEXT_STYLE = "-fx-control-inner-background: red;";
    PyController pyController;

    GridPane inputInfoDisplay = new GridPane();
    Scene inputScene = new Scene(inputInfoDisplay, 600, 600);
    Stage infoStage = new Stage();
    TextField chosenDirLabel = new TextField();
    TextField chosenFileLabel = new TextField();
    TextField chosenXPK2FileLabel = new TextField();
    TextField chosenParamFileLabel = TextFields.createClearableTextField();
    ComboBox<String> B0fieldChoice = new ComboBox();
    TextField tempTextField = new TextField();
    ChoiceBox<String> nucChoice = new ChoiceBox<>();
    TextField pTextField = new TextField();
    ChoiceBox<String> formatChoice = new ChoiceBox<>();
    TextField tauTextField = new TextField();
    TextArea xValTextArea = new TextArea();
    ChoiceBox<String> fitModeChoice = new ChoiceBox<>();
    ChoiceBox<String> peakListChoice = new ChoiceBox<>();
    TextField B1TextField = new TextField();
    TextField yamlTextField = new TextField();
    CheckBox ppmBox = new CheckBox("ppm to Hz");
    ChoiceBox<String> errModeChoice = new ChoiceBox<>();
    TextField errPercentTextField = new TextField();
    ArrayList<HashMap<String, Object>> dataList = new ArrayList();
    Button dirChoiceButton = new Button();
    Button fileChoiceButton = new Button();
    Button xpk2ChoiceButton = new Button();
    Button paramFileChoiceButton = new Button();
    Button addButton = new Button();
    Button clearButton = new Button();
    Button yamlButton = new Button();
    Button loadButton = new Button();
    Path dirPath = null;
    ChoiceBox<String> xConvChoice = new ChoiceBox<>();
    ChoiceBox<String> yConvChoice = new ChoiceBox<>();
    TextField delayC0TextField = new TextField();
    TextField delayDeltaTextField = new TextField();
    TextField delayDelta0TextField = new TextField();

    public InputDataInterface(PyController controller) {
        pyController = controller;
    }

    public void inputParameters() {

        infoStage.setTitle("Input Data Parameters");
        Label fileLabel = new Label("  Value File:  ");
        Label dirLabel = new Label("  Directory:  ");
        Label peakListLabel = new Label("  PeakList:  ");
        Label xpk2FileLabel = new Label("  XPK2 File:  ");
        Label fitFileLabel = new Label("  CoMD/NMR Analysis File:  ");
        Label fieldLabel = new Label("  B0 Field (1H MHz) :  ");
        Label tempLabel = new Label("  Temperature:  ");
        Label nucLabel = new Label("  Nucleus:  ");
        Label pLabel = new Label("  Pressure:  ");
        Label tauLabel = new Label("  Tau:  ");
        Label xValLabel = new Label("  X Values Conversion:  ");
        Label yValLabel = new Label("  Y Values Conversion:  ");
        Label delayLabel = new Label("  Delays:  ");
        Label fitModeLabel = new Label("  Experiment Type:  ");
        Label B1FieldLabel = new Label("  B1 Field:  ");
        Label yamlLabel = new Label("  YAML File:  ");
        Label errModeLabel = new Label("  Error Mode:  ");
        Label errPercentLabel = new Label("  Error Value:  ");

        Label[] labels = {fitModeLabel, peakListLabel, dirLabel, fileLabel, xpk2FileLabel, fitFileLabel, fieldLabel, tempLabel, pLabel,
            tauLabel, B1FieldLabel, nucLabel, errModeLabel, errPercentLabel, xValLabel, delayLabel, yValLabel, yamlLabel};

        dirChoiceButton.setText("Browse");
        dirChoiceButton.setOnAction(e -> chooseDirectory(e));
        chosenDirLabel.setText("");

//        Button fileChoiceButton = new Button();
        fileChoiceButton.setOnAction(e -> chooseFile(e));
        fileChoiceButton.setText("Browse");
        chosenFileLabel.setText("");
        chosenFileLabel.setStyle("-fx-control-inner-background: red;");
        chosenFileLabel.textProperty().addListener((observable, oldValue, newValue)
                -> {
            if (newValue.equals("")) {
                chosenFileLabel.setStyle("-fx-control-inner-background: red;");
            } else {
                chosenFileLabel.setStyle(null);
            }

        });

//        Button xpk2ChoiceButton = new Button();
        xpk2ChoiceButton.setOnAction(e -> chooseXPK2File(e));
        xpk2ChoiceButton.setText("Browse");
        chosenXPK2FileLabel.setText("");
        chosenXPK2FileLabel.setStyle("-fx-control-inner-background: red;");
        chosenXPK2FileLabel.textProperty().addListener((observable, oldValue, newValue)
                -> {
            if (newValue.equals("")) {
                chosenXPK2FileLabel.setStyle("-fx-control-inner-background: red;");
            } else {
                chosenXPK2FileLabel.setStyle(null);
            }

        });

//        Button paramFileChoiceButton = new Button();
        paramFileChoiceButton.setOnAction(e -> chooseParamFile(e));
        paramFileChoiceButton.setText("Browse");
        chosenParamFileLabel.setText("");
        chosenParamFileLabel.setStyle("-fx-control-inner-background: red;");
        chosenParamFileLabel.textProperty().addListener((observable, oldValue, newValue)
                -> {
            if (newValue.equals("")) {
                chosenParamFileLabel.setStyle("-fx-control-inner-background: red;");
            } else {
                chosenParamFileLabel.setStyle(null);
            }

        });

        ppmBox.setSelected(false);

        double textFieldWidth = 100;
        double xValAreaWidth = 150; //240;

        TextField[] textFields = {B1TextField, tauTextField, tempTextField, pTextField,
            errPercentTextField};

        for (TextField textField : textFields) {
            textField.setText("");
            textField.setStyle(INACTIVE_TEXT_STYLE);
            textField.textProperty().addListener((observable, oldValue, newValue) -> updateTextField(textField, newValue));
            textField.setMaxWidth(textFieldWidth);
        }

        xValTextArea.setMaxWidth(xValAreaWidth);
        xValTextArea.setWrapText(true);
        yamlTextField.setText("");

        TextField[] texts = {tempTextField, pTextField, tauTextField, B1TextField};

        inputInfoDisplay.getChildren().clear();

        for (int i = 0; i < labels.length; i++) {
            inputInfoDisplay.add(labels[i], 0, i);
        }
        for (int i = 0; i < texts.length; i++) {
            inputInfoDisplay.add(texts[i], 1, i + 7);
            texts[i].setMaxWidth(textFieldWidth);
        }

        fitModeChoice.getItems().clear();
        fitModeChoice.getItems().addAll(Arrays.asList("Select", "R1", "R2", "NOE", "CPMG", "CEST", "R1RHO"));
        fitModeChoice.setValue("Select");

        fitModeChoice.valueProperty().addListener(x -> {
            updateInfoInterface();
        });

        peakListChoice.getItems().clear();
        peakListChoice.getItems().add("");
        PeakList.peakLists().stream().forEach(p -> peakListChoice.getItems().add(p.getName()));
        peakListChoice.setValue("");
        peakListChoice.valueProperty().addListener(x -> {
            updatePeakList();
        });

        formatChoice.getItems().clear();
        formatChoice.getItems().addAll(Arrays.asList("mpk2", "ires", "txt"));
        formatChoice.setValue("mpk2");

        nucChoice.getItems().clear();
        nucChoice.getItems().addAll(Arrays.asList("H", "D", "F", "P", "C", "N"));
        nucChoice.setValue("H");

        B0fieldChoice.getItems().clear();
        B0fieldChoice.getItems().addAll(Arrays.asList("400", "475", "500", "600", "700", "750", "800", "900", "950", "1000", "1100", "1200"));
        B0fieldChoice.setValue("");
        B0fieldChoice.itemsProperty().addListener((observable, oldValue, newValue)
                -> {
            if (newValue.equals("")) {
                tauTextField.setStyle("-fx-control-inner-background: red;");
            } else {
                tauTextField.setStyle(null);
            }
        });
        B0fieldChoice.setEditable(true);

        EventHandler<ActionEvent> boxevent = new EventHandler<ActionEvent>() {

            public void handle(ActionEvent e) {
                String[] xvals = xValTextArea.getText().split("\t");
                ArrayList<Double> fxvals = new ArrayList();
                String xString = "";
                if ((fitModeChoice.getSelectionModel().getSelectedItem().equals("CEST") || fitModeChoice.getSelectionModel().getSelectedItem().equals("R1RHO"))
                        && ppmBox.isSelected()) {
                    for (int i = 0; i < xvals.length; i++) {
                        fxvals.add(Double.parseDouble(xvals[i]) * Double.parseDouble(B0fieldChoice.getSelectionModel().getSelectedItem().toString()));
                        xString += fxvals.get(i).toString() + "\t";
                    }
                    xValTextArea.setText(xString);
                } else if ((fitModeChoice.getSelectionModel().getSelectedItem().equals("CEST") || fitModeChoice.getSelectionModel().getSelectedItem().equals("R1RHO"))
                        && !ppmBox.isSelected()) {
                    for (int i = 0; i < xvals.length; i++) {
                        fxvals.add(Double.parseDouble(xvals[i]) / Double.parseDouble(B0fieldChoice.getSelectionModel().getSelectedItem().toString()));
                        xString += fxvals.get(i).toString() + "\t";
                    }
                    xValTextArea.setText(xString);
                }
            }

        };

        // set event to checkbox 
        ppmBox.setOnAction(boxevent);

        errModeChoice.getItems().addAll(Arrays.asList("percent", "replicates", "noise", "measured"));
        errModeChoice.setValue("percent");
        errModeChoice.valueProperty().addListener(e -> updateErrorMode());

        xConvChoice.getItems().addAll(Arrays.asList("identity", "tau2", "ppmtohz", "hztoppm", "calc"));
        xConvChoice.setValue("identity");

        xConvChoice.valueProperty().addListener(x -> {
            updateDelays();
        });

        yConvChoice.getItems().addAll(Arrays.asList("identity", "rate", "normalize"));
        yConvChoice.setValue("identity");

        HBox delayBox = new HBox();
        delayBox.getChildren().addAll(new Label("C0:  "), delayC0TextField, new Label("  Delta:  "), delayDeltaTextField, new Label("  Delta0:  "), delayDelta0TextField);

        delayC0TextField.setMaxWidth(textFieldWidth - 20);
        delayDeltaTextField.setMaxWidth(textFieldWidth - 20);
        delayDelta0TextField.setMaxWidth(textFieldWidth - 20);
        int row = 0;
        inputInfoDisplay.add(fitModeChoice, 1, row++);
        inputInfoDisplay.add(peakListChoice, 1, row++);
        inputInfoDisplay.add(dirChoiceButton, 2, row);
        inputInfoDisplay.add(chosenDirLabel, 1, row++);
        inputInfoDisplay.add(fileChoiceButton, 2, row);
        inputInfoDisplay.add(chosenFileLabel, 1, row);
        inputInfoDisplay.add(formatChoice, 3, row++);
        inputInfoDisplay.add(xpk2ChoiceButton, 2, row);
        inputInfoDisplay.add(chosenXPK2FileLabel, 1, row++);
        inputInfoDisplay.add(paramFileChoiceButton, 2, row);
        inputInfoDisplay.add(chosenParamFileLabel, 1, row++);
        inputInfoDisplay.add(B0fieldChoice, 1, row);
        inputInfoDisplay.add(nucChoice, 1, labels.length - 7);
        inputInfoDisplay.add(errModeChoice, 1, labels.length - 6);
        inputInfoDisplay.add(errPercentTextField, 1, labels.length - 5);
//        inputInfoDisplay.add(xValTextArea, 1, labels.length - 2, 1, 1);
//        inputInfoDisplay.add(ppmBox, 2, labels.length - 2);
        inputInfoDisplay.add(xConvChoice, 1, labels.length - 4);
        inputInfoDisplay.add(delayBox, 1, labels.length - 3, 2, 1);
        inputInfoDisplay.add(yConvChoice, 1, labels.length - 2);
        inputInfoDisplay.add(yamlTextField, 1, labels.length - 1);

        chosenFileLabel.setMaxWidth(200);
        chosenXPK2FileLabel.setMaxWidth(200);
        chosenParamFileLabel.setMaxWidth(200);

//        Button addButton = new Button();
        addButton.setOnAction(e -> addInfo(e));
        addButton.setText("Add to Data List");
        inputInfoDisplay.add(addButton, 1, labels.length);

//        Button clearButton = new Button();
        clearButton.setOnAction(e -> clearDataList(e));
        clearButton.setText("Clear Data List");
        inputInfoDisplay.add(clearButton, 1, labels.length + 1);

//        Button yamlButton = new Button();
        yamlButton.setOnAction(e -> makeYAML(e));
        yamlButton.setText("Create YAML");
        yamlButton.disableProperty().bind(yamlTextField.textProperty().isEmpty());
        inputInfoDisplay.add(yamlButton, 2, labels.length - 1);

//        Button loadButton = new Button();
        loadButton.setOnAction(e -> loadInfo(e));
        loadButton.setText("Load");
        inputInfoDisplay.add(loadButton, 2, labels.length + 1);

        updateInfoInterface();

        infoStage.setScene(inputScene);
        infoStage.show();

    }

    void updateTextField(TextField textField, String newValue) {
        if (newValue.equals("")) {
            textField.setStyle(INACTIVE_TEXT_STYLE);
        } else {
            textField.setStyle(null);
        }
    }

    void updateErrorMode() {
        if ((errModeChoice != null) && (errModeChoice.getValue() != null)) {
            if (errModeChoice.getValue().equals("replicates") || errModeChoice.getValue().equals("measured")) {
                errPercentTextField.setDisable(true);
            } else {
                errPercentTextField.setDisable(false);
            }
        }
    }

    public void updateInfoInterface() {
        Button[] buttons = {fileChoiceButton, xpk2ChoiceButton, paramFileChoiceButton, addButton, clearButton, loadButton};
        TextField[] textFields = {B1TextField, tauTextField, tempTextField, pTextField,
            errPercentTextField, yamlTextField, chosenFileLabel, chosenXPK2FileLabel, chosenParamFileLabel};
//                TextField[] textFields = {tempTextField, pTextField,
//                    errPercentTextField, yamlTextField, chosenFileLabel, chosenXPK2FileLabel, chosenParamFileLabel};
        if (fitModeChoice.getSelectionModel().getSelectedItem() != null) {
            if (fitModeChoice.getSelectionModel().getSelectedItem().equals("Select")) {
                for (TextField textField : textFields) {
                    textField.setDisable(true);
                }
                for (Button button : buttons) {
                    button.setDisable(true);
                }
                ppmBox.setDisable(true);
                xValTextArea.setDisable(true);
                errModeChoice.setDisable(true);
                xConvChoice.setDisable(true);
                yConvChoice.setDisable(true);
                formatChoice.setDisable(true);
                nucChoice.setDisable(true);
                B0fieldChoice.setDisable(true);
                delayC0TextField.setDisable(true);
                delayDeltaTextField.setDisable(true);
                delayDelta0TextField.setDisable(true);
            } else if (!fitModeChoice.getSelectionModel().getSelectedItem().equals("Select")) {
                for (TextField textField : textFields) {
                    textField.setDisable(false);
                }
                for (Button button : buttons) {
                    button.setDisable(false);
                }
                formatChoice.setDisable(false);

                if (!peakListChoice.getValue().equals("")) {
                    TextField[] textFields2 = {yamlTextField, chosenFileLabel, chosenXPK2FileLabel, chosenParamFileLabel};
                    for (TextField textField : textFields2) {
                        textField.setDisable(true);
                    }
                    for (Button button : buttons) {
                        button.setDisable(true);
                    }
                    dirChoiceButton.setDisable(true);
                    formatChoice.setDisable(true);
                    loadButton.setDisable(false);
                }
                errModeChoice.getItems().setAll(Arrays.asList("percent", "replicates", "noise", "measured"));
                errModeChoice.setValue("percent");
                xValTextArea.setDisable(false);
                errModeChoice.setDisable(false);
                xConvChoice.setDisable(false);
                yConvChoice.setDisable(false);
                formatChoice.setDisable(false);
                nucChoice.setDisable(false);
                B0fieldChoice.setDisable(false);
                if (fitModeChoice.getSelectionModel().getSelectedItem().equals("CPMG")) {
                    B1TextField.setDisable(true);
                    tauTextField.setDisable(false);
                    ppmBox.setDisable(true);
                    xConvChoice.getItems().clear();
                    xConvChoice.getItems().addAll(Arrays.asList("identity", "tau2"));
                    yConvChoice.getItems().clear();
                    yConvChoice.getItems().addAll(Arrays.asList("identity", "rate"));
                    xConvChoice.setValue("identity");
                    yConvChoice.setValue("rate");
                } else if (fitModeChoice.getSelectionModel().getSelectedItem().equals("EXP")) {
                    B1TextField.setDisable(true);
                    tauTextField.setDisable(true);
                    ppmBox.setDisable(true);
                    xConvChoice.getItems().clear();
                    xConvChoice.getItems().addAll(Arrays.asList("identity", "calc"));
                    yConvChoice.getItems().clear();
                    yConvChoice.getItems().addAll(Arrays.asList("identity", "normalize"));
                    xConvChoice.setValue("identity");
                    yConvChoice.setValue("identity");
                } else if (fitModeChoice.getSelectionModel().getSelectedItem().equals("NOE")) {
                    errModeChoice.getItems().setAll(Arrays.asList("percent", "noise", "measured"));
                    errModeChoice.setValue("noise");
                    B1TextField.setDisable(true);
                    tauTextField.setDisable(true);
                    ppmBox.setDisable(true);
                    xConvChoice.getItems().clear();
                    xConvChoice.getItems().addAll(Arrays.asList("identity"));
                    yConvChoice.getItems().clear();
                    yConvChoice.getItems().addAll(Arrays.asList("normalize"));
                    xConvChoice.setValue("identity");
                    yConvChoice.setValue("normalize");
                } else if ((fitModeChoice.getSelectionModel().getSelectedItem().equals("CEST") || fitModeChoice.getSelectionModel().getSelectedItem().equals("R1RHO"))) {
                    B1TextField.setDisable(false);
                    tauTextField.setDisable(false);
                    ppmBox.setDisable(false);
                    xConvChoice.getItems().clear();
                    xConvChoice.getItems().addAll(Arrays.asList("identity", "ppmtohz", "hztoppm"));
                    yConvChoice.getItems().clear();
                    yConvChoice.getItems().addAll(Arrays.asList("identity", "normalize"));
                    xConvChoice.setValue("identity");
                    yConvChoice.setValue("identity");
                    if (fitModeChoice.getSelectionModel().getSelectedItem().equals("CEST")) {
                        yConvChoice.setValue("normalize");
                    }
                }
            }
        }

    }

    public void updateDelays() {
        if (!fitModeChoice.getSelectionModel().getSelectedItem().equals("Select")
                && (xConvChoice.getSelectionModel().getSelectedItem() != null) && xConvChoice.getSelectionModel().getSelectedItem().equals("calc")) {
            delayC0TextField.setDisable(false);
            delayDeltaTextField.setDisable(false);
            delayDelta0TextField.setDisable(false);
        } else {
            delayC0TextField.setDisable(true);
            delayDeltaTextField.setDisable(true);
            delayDelta0TextField.setDisable(true);
        }
    }

    public void chooseDirectory(ActionEvent event) {
        DirectoryChooser dirChooser = new DirectoryChooser();
        File file = dirChooser.showDialog(infoStage);
        if (file != null) {
            chosenDirLabel.setText(file.toString());
            dirPath = file.toPath();
        }
    }

    public void chooseFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (dirPath != null) {
            File userDirectory = new File(dirPath.toString());
            fileChooser.setInitialDirectory(userDirectory);
        }
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("mpk2 or txt File", "*.mpk2", "*.txt"));
        File file = fileChooser.showOpenDialog(infoStage);
        if (file != null) {
            Path path = dirPath.relativize(file.toPath());
            String pathString = path.toString();
            chosenFileLabel.setText(pathString);
            if (pathString.contains(".")) {
                String fileTail = pathString.substring(0, pathString.lastIndexOf('.'));
                yamlTextField.setText(fileTail + ".yaml");
                if (pathString.endsWith(".mpk2")) {
                    FileSystem fileSystem = FileSystems.getDefault();
                    File xpk2File = fileSystem.getPath(file.getParent(), fileTail + ".xpk2").toFile();
                    if (xpk2File.canRead()) {
                        parseXPK2File(xpk2File);
                    }
                }
            }
        }
    }

    public void chooseXPK2File(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (dirPath != null) {
            File userDirectory = new File(dirPath.toString());
            fileChooser.setInitialDirectory(userDirectory);
        }
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("xpk2 File", "*.xpk2"));
        File file = fileChooser.showOpenDialog(infoStage);
        if (file != null) {
            parseXPK2File(file);
        }
    }

    void parseXPK2File(File file) {
        Path path = dirPath.relativize(file.toPath());
        chosenXPK2FileLabel.setText(path.toString());

        Path path1 = file.toPath();

        List<String[]> head = new ArrayList<>();

        try (BufferedReader fileReader = Files.newBufferedReader(path1)) {
            while (true) {
                String line = fileReader.readLine();
                if (line == null) {
                    break;
                }
                String sline = line.trim();
                if (sline.length() == 0) {
                    continue;
                }
                if (sline.startsWith("id")) {
                    break;
                }
                String[] sline1 = line.split("\t", -1);
                head.add(sline1);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        int sfInd = Arrays.asList(head.get(2)).indexOf("sf");
        int codeInd = Arrays.asList(head.get(2)).indexOf("code");
        String field = Arrays.asList(head.get(3)).get(sfInd);
        String nuc = Arrays.asList(head.get(4)).get(codeInd);
        String nuc1 = nuc.replaceAll("[^a-zA-Z]", "");
        String nuc2 = nuc.replaceAll("[a-zA-Z]", "");
        nucChoice.setValue(nuc1);
        B0fieldChoice.getSelectionModel().select(field);
    }

    void updatePeakList() {
        updateInfoInterface();
        PeakList peakList = PeakList.get(peakListChoice.getValue());
        if (peakList != null) {
            int peakDim = 1;
            String peakListName = peakList.getName();
            List<Number> vcpmgList = null;
            String nucleus;
            double B0field;
            double temperature;
            DatasetBase dataset = DatasetBase.getDataset(peakList.fileName);
            if (dataset == null) {
                nucleus = peakList.getSpectralDim(0).getNucleus();
                B0field = peakList.getSpectralDim(0).getSf();
                temperature = 298.14;
            } else {
                nucleus = dataset.getNucleus(peakDim).getName();
                B0field = dataset.getSf(0);
                temperature = dataset.getTempK();
                System.out.println(temperature);
            }

            nucChoice.setValue(nucleus);
            B0fieldChoice.setValue(String.valueOf(B0field));
            tempTextField.setText(String.valueOf(temperature));

        }
    }

    Double getDouble(String str) {
        Double value;
        try {
            value = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            value = null;
        }
        return value;
    }

    void loadFromPeakList() {
        PeakList peakList = PeakList.get(peakListChoice.getValue());
        if (peakList != null) {
            
            MoleculeBase mol = MoleculeFactory.getActive();
            boolean dynCreateMol;
            if (mol == null) {
                if (!GUIUtils.affirm("No molecule present, dynamically create")) {
                    return;
                } else {
                    dynCreateMol = true;
                }
            } else {
                dynCreateMol = false;
            }
            DynamicsSource dynSource = new DynamicsSource(false, false, dynCreateMol, dynCreateMol);
            String peakListName = peakList.getName();
            ExperimentSet experimentSet = new ExperimentSet(peakListName, peakListName);
            String expMode = fitModeChoice.getValue().toLowerCase();
            experimentSet.setExpMode(expMode);
            double[] delayCalc = {0.0, 0.0, 1.0};
            HashMap<String, Object> errorPars = new HashMap<>();
            Double temperatureK = getDouble(tempTextField.getText());
            Double tau = getDouble(tauTextField.getText());
            Double B1field = getDouble(B1TextField.getText());

            Experiment expData;
            switch (expMode) {
                case "T1":
                    expData = new T1Experiment(experimentSet, peakList.getName(),
                            nucChoice.getValue(), Double.parseDouble(B0fieldChoice.getValue()),
                            temperatureK);
                default:
                    expData = new Experiment(experimentSet, peakList.getName(),
                            nucChoice.getValue(), Double.parseDouble(B0fieldChoice.getValue()),
                            temperatureK, expMode);
            }
//            tau, null,
//                    fitModeChoice.getValue(),
//                    errorPars, delayCalc, B1field);

            try {
                DataIO.loadFromPeakList(peakList, expData, experimentSet,
                        xConvChoice.getValue(), yConvChoice.getValue(), dynSource);
                if (experimentSet != null) {
                    ResidueChart reschartNode = PyController.mainController.getActiveChart();
                    if (reschartNode == null) {
                        reschartNode = PyController.mainController.addChart();

                    }
                    ChartUtil.addResidueProperty(experimentSet.getName(), experimentSet);
                    String parName = "Kex";
                    if (experimentSet.getExpMode().equals("r1")) {
                        parName = "R";
                    } else if (experimentSet.getExpMode().equals("r2")) {
                        parName = "R";
                    } else if (experimentSet.getExpMode().equals("noe")) {
                        parName = "NOE";
                    }
                    ObservableList<DataSeries> data = ChartUtil.getParMapData(experimentSet.getName(), "best", "0:0:0", parName);
                    PyController.mainController.setCurrentExperimentSet(experimentSet);
                    PyController.mainController.makeAxisMenu();
                    PyController.mainController.setYAxisType(experimentSet.getExpMode(), experimentSet.getName(), "best", "0:0:0", parName);
                    reschartNode.setResProps(experimentSet);
                    PyController.mainController.setControls();
                }
            } catch (IllegalArgumentException iAE) {
                GUIUtils.warn("Load from peak list", iAE.getMessage());
            }
        }
    }

    public void chooseParamFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        if (dirPath != null) {
            File userDirectory = new File(dirPath.toString());
            fileChooser.setInitialDirectory(userDirectory);
        }
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("txt File", "*.txt"));
        File file = fileChooser.showOpenDialog(infoStage);
        if (file != null) {
            String directory = file.getParent();
            String fileName = file.getName();
            chosenParamFileLabel.setText(directory + "/" + fileName);
        }
    }

    public void clearParamFile(ActionEvent event) {
        chosenParamFileLabel.setText("");
    }

    public void addInfo(ActionEvent event) {
        addInfo();
    }

    public void addInfo() {
        HashMap hm = new HashMap();
        hm.put("file", chosenFileLabel.getText());

        hm.put("paramFile", chosenParamFileLabel.getText());
        hm.put("temperature", Double.parseDouble(tempTextField.getText()));
        hm.put("xconv", xConvChoice.getSelectionModel().getSelectedItem());
        hm.put("yconv", yConvChoice.getSelectionModel().getSelectedItem());
        hm.put("B0", Double.parseDouble(B0fieldChoice.getSelectionModel().getSelectedItem().toString()));
        hm.put("nucleus", nucChoice.getSelectionModel().getSelectedItem().replaceAll("[^a-zA-Z]", ""));
        if (!tauTextField.isDisabled()) {
            hm.put("tau", Double.parseDouble(tauTextField.getText()));
        }
        hm.put("pressure", Double.parseDouble(pTextField.getText()));
        hm.put("format", formatChoice.getSelectionModel().getSelectedItem());
        hm.put("fitmode", fitModeChoice.getSelectionModel().getSelectedItem().toLowerCase());
        if (!B1TextField.isDisabled()) {
            hm.put("B1", Double.parseDouble(B1TextField.getText()));
        }
        HashMap hmde = new HashMap();
        hmde.put("mode", errModeChoice.getValue());
        if (!errModeChoice.getValue().equals("replicates") && !errModeChoice.getValue().equals("measured")) {
            hmde.put("value", Double.parseDouble(errPercentTextField.getText()));
        }
        HashMap hmdd = new HashMap();
        if (!delayC0TextField.getText().equals("") && !delayDeltaTextField.getText().equals("") && !delayDelta0TextField.getText().equals("")) {
            hmdd.put("c0", Double.parseDouble(delayC0TextField.getText()));
            hmdd.put("delta0", Double.parseDouble(delayDelta0TextField.getText()));
            hmdd.put("delta", Double.parseDouble(delayDeltaTextField.getText()));
        }

        hm.put("error", hmde);
        if (hm.get("xconv").equals("calc")) {
            hm.put("delays", hmdd);
        }

        String[] xvals = xValTextArea.getText().trim().split("\t");
        if (xvals.length > 0) {
            ArrayList<Double> fxvals = new ArrayList();
            try {
                for (String xval : xvals) {
                    fxvals.add(Double.parseDouble(xval));
                }
            } catch (NumberFormatException nfe) {
                fxvals = null;
            }
            hm.put("vcpmg", fxvals);
        }
        dataList.add(hm);
//        String fileTail = chosenFileLabel.getText().substring(0, chosenFileLabel.getText().lastIndexOf('.'));
//        yamlTextField.setText(fileTail + ".yaml");
//        for (int i=0; i<dataList.size(); i++) {
//            System.out.println("dataList " + i + " " + dataList.get(i));
//        }
    }

    public void makeYAML(ActionEvent event) {
        if (dataList.isEmpty()) {
            addInfo();
        }
        makeYAML(dataList);
        dataList.clear();
    }

    public void makeYAML(List data) {
        HashMap hm1 = new HashMap();
        HashMap hm2 = new HashMap();
        ArrayList<HashMap<String, Object>> dataHmList = new ArrayList();

        for (int i = 0; i < data.size(); i++) {
            HashMap hmdf = (HashMap) data.get(i);
            HashMap hmd = new HashMap(hmdf);

            String paramFile = (String) hmdf.get("paramFile");
            String paramFileName = paramFile.substring(paramFile.lastIndexOf("/") + 1, paramFile.length());
            hm2.put("mode", hmdf.get("fitmode"));
            if (!paramFileName.equals("")) {
                hm2.put("file", paramFileName);
            } else {
                hm2.put("file", "analysis.txt");
            }
            Set keySet = hmd.keySet();
            if (!hmd.get("fitmode").equals("cest") && !hmd.get("fitmode").equals("r1rho")) {
                keySet.remove("B1");
            }
            if ((hmd.get("vcpmg") == null) || (hmd.get("vcpmg").toString().equals(""))) {
                keySet.remove("vcpmg");
            }
            if (!hmd.get("xconv").equals("calc")) {
                keySet.remove("delays");
            }
            keySet.remove("fitmode");
            keySet.remove("paramFile");
            hmd.keySet().retainAll(keySet);
            String dataFile = (String) hmdf.get("file");
            String dataFileName = dataFile.substring(dataFile.lastIndexOf("/") + 1, dataFile.length());
            hmd.put("file", dataFileName);
            dataHmList.add(hmd);
        }
        hm2.put("data", dataHmList);
        hm1.put("fit", hm2);

        Yaml yaml = new Yaml();
        String s = yaml.dumpAsMap(hm1);
        Path path = FileSystems.getDefault().getPath(dirPath.toString(), yamlTextField.getText());
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write(s);
            System.out.println(yamlTextField.getText() + " written");
        } catch (IOException ex) {
            Logger.getLogger(DataIO.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void clearDataList(ActionEvent event) {
        dataList.clear();
    }

    public void loadInfo(ActionEvent event) {
        if (!peakListChoice.getValue().equals("")) {
            loadFromPeakList();
            return;
        }
        if (dataList.isEmpty()) {
            addInfo();
        }
        String projectName = yamlTextField.getText().trim();
        if (projectName.length() == 0) {

        } else if (projectName.endsWith(".yaml")) {
            projectName = projectName.substring(0, projectName.indexOf(".yaml"));
        }
        File projectDirFile = new File(chosenDirLabel.getText().trim());
        dirPath = projectDirFile.toPath();

        ExperimentSet resProp = null;
        String expMode = fitModeChoice.getSelectionModel().getSelectedItem().toLowerCase();
        resProp = new ExperimentSet(projectName, projectDirFile.toString());
        expMode = expMode.toLowerCase();
        resProp.setExpMode(expMode);

        try {
            DataIO.processYAMLDataSections(resProp, dirPath, expMode, dataList);
        } catch (IOException ex) {
            ExceptionDialog dialog = new ExceptionDialog(ex);
            dialog.showAndWait();
            return;
        }

        PyController.mainController.clearSecondaryStructure();
        if (PyController.mainController.activeChart != null) {
            PyController.mainController.clearChart();
            PyController.mainController.chartInfo.clear();
            PyController.mainController.simulate = false;
            PyController.mainController.fitResult = null;
        }
        ResidueChart reschartNode = PyController.mainController.getActiveChart();
        if (reschartNode == null) {
            reschartNode = PyController.mainController.addChart();

        }
//            ExperimentSet resProp = DataIO.loadParameters(fileName);
        ChartUtil.addResidueProperty(resProp.getName(), resProp);
        String parName = "Kex";
        if (resProp.getExpMode().equals("r1")) {
            parName = "R";
        } else if (resProp.getExpMode().equals("r2")) {
            parName = "R";
        }
        ObservableList<DataSeries> data = ChartUtil.getParMapData(resProp.getName(), "best", "0:0:0", parName);
        PyController.mainController.setCurrentExperimentSet(resProp);
        PyController.mainController.makeAxisMenu();
        PyController.mainController.setYAxisType(resProp.getExpMode(), resProp.getName(), "best", "0:0:0", parName);
        reschartNode.setResProps(resProp);
        PyController.mainController.setControls();

        fitModeChoice.setValue("Select");
        dataList.clear();
        updateInfoInterface();
    }

}
