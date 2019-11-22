import FileManager.ReceivedDroppedFiles;
import FileManager.WorkingDirectory;
import net.iharder.dnd.FileDrop;
import version4.ReportPDF.MergeReport;
import version4.Collection;
import version4.tableModels.AnalysisTable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ExecutionException;

public class Scatter {
    private static String OUTPUT_DIR_SUBTRACTION_NAME="";
    private static String ATSAS_DIRECTORY="";
    private static String THRESHOLD="";

    public static String version = "IV.a";
    public static WorkingDirectory WORKING_DIRECTORY;

    public MergeReport mergeReport; //singleton

    private JPanel bg;
    private JPanel mainPanel;
    private JPanel sidePanel;
    private JPanel analysisButton;
    private JLabel analysisLabel;
    private JProgressBar mainProgressBar;
    private JPanel analysisPane;
    private JLabel prButtonLabel;
    private JPanel prButtonPanel;
    private JLabel prIcon;
    private JLabel analIcon;
    private JLabel secLabel;
    private JPanel secButton;
    private JLabel secIcon;
    private JPanel subtractPanel;
    private JLabel subtractLabel;
    private JLabel subIcon;
    private JLabel mainNameLabel;
    private JPanel modelPanel;
    private JLabel modelLabel;
    private JLabel modelIcon;
    private JLabel status;
    private JCheckBox convertNmToAngstromCheckBox;
    private JCheckBox autoRgCheckBox;
    private JPanel SettingsPanel;
    private JLabel settingsLabel;
    private JLabel settingsIcon;
    public static Collection collectionSelected;
    public static Color background;
    public static boolean useAutoRg = true;
    public static boolean convertNmToAngstrom = false;

    private ArrayList<SideButtonIcon> sideButtons;
    private int totalSideButtons;

    private Analysis analysisTab;
    private RealSpacePr realSpacePr;
    public static AnalysisTable analysisTable;
    private SECTool secTab;
    private Settings settingsTab;
    private Modeling modeling;
    private Subtract subtractTab;
    private static int cpuCores;

    public Scatter(){

        mergeReport = MergeReport.getInstance();

        collectionSelected = new Collection("mainCollection");
        //mainProgressBar.setForeground(Color.white);
        //settingsTab = new Settings(WORKING_DIRECTORY, status);
        settingsTab = Settings.getInstance();
        settingsTab.setFields(WORKING_DIRECTORY, status);
        modeling = Modeling.getInstance();
        modeling.setFields(WORKING_DIRECTORY, status);

        cpuCores = Runtime.getRuntime().availableProcessors();
        Vector comboBoxItemsCPU = new Vector();
        for (int i=1; i<=cpuCores; i++){
            comboBoxItemsCPU.add(i);
        }

        final DefaultComboBoxModel cpuModel = new DefaultComboBoxModel(comboBoxItemsCPU);

        mainProgressBar.setUI(new BasicProgressBarUI() {
            protected Color getSelectionBackground() { return Color.white; }
            protected Color getSelectionForeground() { return Color.black; }
            //protected Color getForeground() { return Color.black; }
        });

        analysisTab = new Analysis(WORKING_DIRECTORY, status, mainProgressBar);
        background = analysisTab.getMainBackgroundColor();

        secTab = new SECTool(status, mainProgressBar, convertNmToAngstromCheckBox);
        subtractTab = new Subtract(status, mainProgressBar, convertNmToAngstromCheckBox);

        realSpacePr = new RealSpacePr(
                WORKING_DIRECTORY,
                mainProgressBar,
                status,
                settingsTab.getDefaultDmax(),
                settingsTab.getRejectionCutOffBox(),
                settingsTab.getcBox(),
                settingsTab.getRefinementRoundsBox()
        );


        analysisPane.setBackground(Color.BLACK);
        analysisPane.setLayout(new BoxLayout(analysisPane, BoxLayout.PAGE_AXIS));
        analysisPane.add(analysisTab.getPanel());

        analysisTable = AnalysisTable.getInstance();
        analysisTable.setTableModel(status, WORKING_DIRECTORY, collectionSelected, analysisTab.getMainBackgroundColor());

        analysisTab.setTableModel();
        JScrollPane analysisList = new JScrollPane(analysisTable.getTable());
        analysisList.setOpaque(true);
        analysisTab.getDataPanel().add(analysisList);
        collectionSelected.addPropertyChangeListener(analysisTable.getModel());
        analysisTab.setDropDownItems(analysisTable.getTable(), mainProgressBar);
        // add drop down menu stuff


        new FileDrop(analysisTab.getDataPanel(), new FileDrop.Listener() {

            @Override
            public void filesDropped(final File[] files) {

                collectionSelected.setPanelID(0);

                new Thread() {
                    public void run() {

                        try {
                            ReceivedDroppedFiles rec1 = new ReceivedDroppedFiles(files, collectionSelected, analysisTable.getModel(), status, convertNmToAngstromCheckBox.isSelected(), autoRgCheckBox.isSelected(), false, mainProgressBar, WORKING_DIRECTORY.getWorkingDirectory());
//                        // add other attributes and then run
//                        rec1.setModels(analysisModel, resultsModel, dataFilesModel, dataFilesList);
//                        rec1.setPDBParams(excludeWatersFromInputCheckBox.isSelected(), Double.parseDouble(qmaxForPDBText.getText()));
                            rec1.run();
                            rec1.get();
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        } catch (ExecutionException e1) {
                            e1.printStackTrace();
                        }

                    }
                }.start();
            }
        });

        mainNameLabel.setForeground(background);
        /*
         add side buttons in order as they appear in form
         */
        sideButtons = new ArrayList<>();
        sideButtons.add(new SideButtonIcon(analysisButton, analIcon, analysisLabel));
        sideButtons.add(new SideButtonIcon(prButtonPanel, prIcon, prButtonLabel));
        sideButtons.add(new SideButtonIcon(secButton, secIcon, secLabel));
        sideButtons.add(new SideButtonIcon(subtractPanel, subIcon, subtractLabel));
        sideButtons.add(new SideButtonIcon(modelPanel, modelIcon, modelLabel));
        sideButtons.add(new SideButtonIcon(SettingsPanel, settingsIcon, settingsLabel));

        totalSideButtons = sideButtons.size();
        for(int i=0; i<totalSideButtons; i++){
            sideButtons.get(i).changeBackgroundColor(background);
        }

        analysisButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                sideButtons.get(0).highlight(new Color(198,248,255));
            }
        });

        analysisButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                sideButtons.get(0).dehighlight();
            }
        });

        prButtonPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                sideButtons.get(1).highlight(new Color(198,248,255));
            }
        });

        prButtonPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                sideButtons.get(1).dehighlight();
            }
        });

        secButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                sideButtons.get(2).highlight(new Color(198,248,255));
            }
        });

        secButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                sideButtons.get(2).dehighlight();
            }
        });

        subtractPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                sideButtons.get(3).highlight(new Color(198,248,255));
            }
        });

        subtractPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                sideButtons.get(3).dehighlight();
            }
        });

        modelPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                sideButtons.get(4).highlight(new Color(198,248,255));
            }
        });

        modelPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                sideButtons.get(4).dehighlight();
            }
        });

        SettingsPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                super.mouseEntered(e);
                sideButtons.get(5).highlight(new Color(198,248,255));
            }
        });

        SettingsPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                super.mouseExited(e);
                sideButtons.get(5).dehighlight();
            }
        });


        prButtonPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                analysisPane.removeAll();
                analysisPane.repaint();
                dehighlightSideButtons();
                sideButtons.get(1).highlight(new Color(198,248,255));
                analysisPane.add(realSpacePr.getPanel());

                if (collectionSelected.getTotalDatasets() == 0){
                    Toolkit.getDefaultToolkit().beep();
                    status.setText("No data to use");
                    return;
                }

                analysisPane.revalidate();
                analysisPane.repaint();
            }
        });


        analysisButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                analysisPane.removeAll();
                dehighlightSideButtons();
                sideButtons.get(0).highlight(new Color(198,248,255));
                analysisPane.add(analysisTab.getPanel());
                analysisPane.revalidate();
                analysisPane.repaint();
            }
        });

        secButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                analysisPane.removeAll();
                dehighlightSideButtons();
                sideButtons.get(2).highlight(new Color(198,248,255));
                analysisPane.add(secTab.getPanel());
                analysisPane.revalidate();
                analysisPane.repaint();
                System.out.println("centermain " + analysisPane.getWidth());
                secTab.printDimensions();
            }
        });


        subtractPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                analysisPane.removeAll();
                dehighlightSideButtons();
                sideButtons.get(3).highlight(new Color(198,248,255));
                analysisPane.add(subtractTab.getPanel());
                analysisPane.revalidate();
                analysisPane.repaint();
//                System.out.println("centermain " + analysisPane.getWidth());
//                subtractTab.printDimensions();
            }
        });

        modelPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                analysisPane.removeAll();
                dehighlightSideButtons();
                sideButtons.get(4).highlight(new Color(198,248,255));
                analysisPane.add(modeling.getPanel());
                analysisPane.revalidate();
                analysisPane.repaint();
            }
        });


        SettingsPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                analysisPane.removeAll();
                dehighlightSideButtons();
                sideButtons.get(5).highlight(new Color(198,248,255));
                analysisPane.add(settingsTab.getPanel());
                analysisPane.revalidate();
                analysisPane.repaint();
            }
        });


        autoRgCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                useAutoRg = autoRgCheckBox.isSelected();
            }
        });


        convertNmToAngstromCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                convertNmToAngstrom = convertNmToAngstromCheckBox.isSelected();
            }
        });
    }

    public static void main(String[] args) {

        WORKING_DIRECTORY = new WorkingDirectory();
        File propertyFile = new File("scatter.config");

        if (propertyFile.exists() && !propertyFile.isDirectory()){

            Properties prop = new Properties();
            InputStream input = null;

            try {
                input = new FileInputStream("scatter.config");
                // load a properties file
                prop.load(input);

                if (prop.getProperty("workingDirectory") != null) {
                    //WORKING_DIRECTORY_NAME = prop.getProperty("workingDirectory");
                    WORKING_DIRECTORY.setWorkingDirectory(prop.getProperty("workingDirectory"));
                    // WORKING_DIRECTORY = new WorkingDirectory(prop.getProperty("workingDirectory"));
                }
                if (prop.getProperty("atsasDirectory") != null) {
                    ATSAS_DIRECTORY = prop.getProperty("atsasDirectory");
                    Settings setIt = Settings.getInstance();
                    setIt.setATSASDir(ATSAS_DIRECTORY);
                }
                if (prop.getProperty("subtractionDirectory") != null) {
                    OUTPUT_DIR_SUBTRACTION_NAME = prop.getProperty("subtractionDirectory");
                }

            } catch (IOException ex) {

                ex.printStackTrace();
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        File theDir = new File(WORKING_DIRECTORY.getWorkingDirectory());
        if (!theDir.exists()) {
            WORKING_DIRECTORY = new WorkingDirectory(System.getProperty("user.dir"));
        }

        JFrame frame = new JFrame("ScatterIV");
        frame.setContentPane(new Scatter().bg);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    public static void updateProp(){
        Properties prop = new Properties();
        OutputStream output = null;

        Settings setit = Settings.getInstance();
        ATSAS_DIRECTORY = setit.getATSASDir();
        System.out.println("Updating prop " + ATSAS_DIRECTORY);
        try {
            output = new FileOutputStream("scatter.config");

            // set the properties value
            prop.setProperty("workingDirectory", WORKING_DIRECTORY.getWorkingDirectory());
            prop.setProperty("atsasDirectory", ATSAS_DIRECTORY);
            prop.setProperty("subtractionDirectory", OUTPUT_DIR_SUBTRACTION_NAME);
            // save properties to project root folder
            prop.store(output, null);

        } catch (IOException io) {
            io.printStackTrace();
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }



    private void dehighlightSideButtons(){
        for(int i=0; i<totalSideButtons; i++){
            sideButtons.get(i).dehighlight();
        }
    }


    public void changeCWD(){
        status.setText("CWD : " + WORKING_DIRECTORY.getWorkingDirectory());
        File theCWD = new File(WORKING_DIRECTORY.getWorkingDirectory());
        JFileChooser chooser = new JFileChooser(theCWD);
        chooser.setDialogTitle("Select Directory");

        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        if (chooser.showOpenDialog(this.bg) == JFileChooser.APPROVE_OPTION){
            //WORKING_DIRECTORY.setWorkingDirectory(chooser.getCurrentDirectory().toString());
            if (chooser.getSelectedFile().isDirectory()){
                WORKING_DIRECTORY.setWorkingDirectory(chooser.getSelectedFile().toString());
            } else {
                WORKING_DIRECTORY.setWorkingDirectory(chooser.getCurrentDirectory().toString());
            }
            updateProp();
        }

        status.setText("CWD Changed to : " + WORKING_DIRECTORY.getWorkingDirectory());
    }

}
