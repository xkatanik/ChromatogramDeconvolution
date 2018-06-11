package cz.muni.fi.chrom_deconvolution;

import com.google.common.collect.Range;
import net.sf.mzmine.datamodel.MZmineProject;
import net.sf.mzmine.main.MZmineConfiguration;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.main.impl.MZmineConfigurationImpl;
import net.sf.mzmine.modules.MZmineProcessingStep;
import net.sf.mzmine.modules.impl.MZmineProcessingStepImpl;
import net.sf.mzmine.modules.peaklistmethods.io.xmlexport.XMLExportParameters;
import net.sf.mzmine.modules.peaklistmethods.io.xmlexport.XMLExportTask;
import net.sf.mzmine.modules.peaklistmethods.io.xmlimport.XMLImportParameters;
import net.sf.mzmine.modules.peaklistmethods.io.xmlimport.XMLImportTask;
import net.sf.mzmine.modules.peaklistmethods.peakpicking.deconvolution.ADAPpeakpicking.*;
import net.sf.mzmine.modules.peaklistmethods.peakpicking.deconvolution.DeconvolutionParameters;
import net.sf.mzmine.modules.peaklistmethods.peakpicking.deconvolution.DeconvolutionTask;
import net.sf.mzmine.modules.peaklistmethods.peakpicking.deconvolution.PeakResolver;
import net.sf.mzmine.modules.rawdatamethods.rawdataimport.fileformats.NetCDFReadTask;
import net.sf.mzmine.parameters.parametertypes.ranges.DoubleRangeParameter;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsSelection;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsSelectionType;
import net.sf.mzmine.project.impl.MZmineProjectImpl;
import net.sf.mzmine.project.impl.ProjectManagerImpl;
import net.sf.mzmine.project.impl.RawDataFileImpl;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Chromatogram deconvolution module
 *
 * @author Kristian Katanik
 */
public class Main {

    public static void main(String[] args) throws IOException, NoSuchFieldException, IllegalAccessException {

        String inputFileName;
        String outputFileName;
        String rawData;
        Double SNThreshold = 10.0;
        Double minFeatureHeight = 10.0;
        Double areaThreshold = 110.0;
        Double peakDurationRange1 = 0.00;
        Double peakDurationRange2 = 10.00;
        Double RTRange1 = 0.00;
        Double RTRange2 = 0.10;
        //For Wavelet SN estimator
        Boolean wavelet = false;
        Double peakWidth = 3.0;
        Boolean abs = true;

        Options options = setOptions();
        String header = "";
        String footer = "Created by Kristian Katanik, version 1.1.";

        if (args.length == 0) {
            HelpFormatter helpFormatter = new HelpFormatter();
            helpFormatter.setOptionComparator(null);
            helpFormatter.printHelp("Chromatogram deconvolution module help.", header, options, footer, true);
            System.exit(1);
            return;
        }

        CommandLine commandLine;
        try {
            commandLine = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            for (String arg : args) {
                if (arg.equals("-h") || arg.equals("--help")) {
                    HelpFormatter helpFormatter = new HelpFormatter();
                    helpFormatter.setOptionComparator(null);
                    helpFormatter.printHelp("Chromatogram deconvolution module help.", header, options, footer, true);
                    System.exit(1);
                    return;
                }
            }
            System.err.println("Some of the required parameters or their arguments are missing. Use -h or --help for help.");
            System.exit(1);
            return;
        }

        inputFileName = commandLine.getOptionValue("i");
        outputFileName = commandLine.getOptionValue("o");
        rawData = commandLine.getOptionValue("r");

        if (commandLine.hasOption("snt")) {
            try {
                SNThreshold = Double.parseDouble(commandLine.getOptionValue("snt"));
            } catch (NumberFormatException e) {
                System.err.println("Wrong format of SNThreshold value. Value has to be number in double format.");
                System.exit(1);
                return;
            }
        }
        if (commandLine.hasOption("mfh")) {
            try {
                minFeatureHeight = Double.parseDouble(commandLine.getOptionValue("mfh"));
            } catch (NumberFormatException e) {
                System.err.println("Wrong format of minFeatureHeight value. Value has to be number in double format.");
                System.exit(1);
                return;
            }
        }
        if (commandLine.hasOption("at")) {
            try {
                areaThreshold = Double.parseDouble(commandLine.getOptionValue("at"));
            } catch (NumberFormatException e) {
                System.err.println("Wrong format of areaThreshold value. Value has to be number in double format.");
                System.exit(1);
                return;
            }
        }
        if (commandLine.hasOption("pdr")) {
            if (commandLine.getOptionValues("pdr").length != 2) {
                System.err.println("Peak duration range has to have exactly 2 arguments.");
                System.exit(1);
                return;
            }
            try {
                peakDurationRange1 = Double.parseDouble(commandLine.getOptionValues("pdr")[0]);
                peakDurationRange2 = Double.parseDouble(commandLine.getOptionValues("pdr")[1]);
            } catch (NumberFormatException e) {
                System.err.println("Wrong format of peakDurationRange value(s). Value has to be number in double format.");
                System.exit(1);
                return;
            }
        }
        if (commandLine.hasOption("rtr")) {
            if (commandLine.getOptionValues("rtr").length != 2) {
                System.err.println("RT range has to have exactly 2 arguments.");
                System.exit(1);
                return;
            }
            try {
                RTRange1 = Double.parseDouble(commandLine.getOptionValues("rtr")[0]);
                RTRange2 = Double.parseDouble(commandLine.getOptionValues("rtr")[1]);
            } catch (NumberFormatException e) {
                System.err.println("Wrong format of RTRange value(s). Value has to be number in double format.");
                System.exit(1);
                return;
            }
        }
        if (commandLine.hasOption("w")) {
            wavelet = true;
            if (commandLine.hasOption("pw")) {
                try {
                    peakWidth = Double.parseDouble(commandLine.getOptionValue("pw"));
                } catch (NumberFormatException e) {
                    System.err.println("Wrong format of peakWidth value. Value has to be number in double format.");
                    System.exit(1);
                    return;
                }
            }
            abs = commandLine.hasOption("a");
        }

        File inputFile;
        try {
            inputFile = new File(inputFileName);
        } catch (Exception e) {
            System.out.println("Unable to load input file.");
            System.exit(1);
            return;
        }

        File rawInputFile;
        try {
            rawInputFile = new File(rawData);
        } catch (Exception e) {
            System.out.println("Unable to load raw file.");
            System.exit(1);
            return;
        }

        File outputFile;
        try {
            outputFile = new File(outputFileName);
        } catch (Exception e) {
            System.out.println("Unable to create/load output file.");
            System.exit(1);
            return;
        }

        if (!inputFile.exists() || inputFile.isDirectory() || !rawInputFile.exists() || rawInputFile.isDirectory()) {
            System.err.println("Unable to load input/raw file.");
            System.exit(1);
            return;
        }

        final MZmineProject mZmineProject = new MZmineProjectImpl();

        //code for raw data
        RawDataFileImpl rawDataFile2;
        try {
            rawDataFile2 = new RawDataFileImpl(rawInputFile.getName());
        } catch (IOException e) {
            System.err.println("Unable to open raw data file.");
            System.exit(1);
            return;
        }

        NetCDFReadTask netCDFReadTask = new NetCDFReadTask(mZmineProject, rawInputFile, rawDataFile2);
        netCDFReadTask.run();
        mZmineProject.addFile(rawDataFile2);


        XMLImportParameters xmlImportParameters = new XMLImportParameters();
        xmlImportParameters.getParameter(XMLImportParameters.filename).setValue(inputFile);
        XMLImportTask xmlImportTask = new XMLImportTask(mZmineProject, xmlImportParameters);
        xmlImportTask.run();

        //Configuration
        MZmineConfiguration configuration = new MZmineConfigurationImpl();
        Field configurationField = MZmineCore.class.getDeclaredField("configuration");
        configurationField.setAccessible(true);
        configurationField.set(null, configuration);

        ProjectManagerImpl projectManager = new ProjectManagerImpl();
        Field projectManagerField = MZmineCore.class.getDeclaredField("projectManager");
        projectManagerField.setAccessible(true);
        projectManagerField.set(null, projectManager);
        projectManager.setCurrentProject(mZmineProject);

        ADAPDetectorParameters adapDetectorParameters = setADAPDetectorParameters(wavelet, peakWidth, abs, peakDurationRange1,
                peakDurationRange2, RTRange1, RTRange2, SNThreshold, minFeatureHeight, areaThreshold);

        ADAPDetector adapDetector = new ADAPDetector();

        MZmineProcessingStep<PeakResolver> peakResolver = new MZmineProcessingStepImpl<PeakResolver>(adapDetector, adapDetectorParameters);


        PeakListsSelection peakListsSelection = new PeakListsSelection();
        peakListsSelection.setSelectionType(PeakListsSelectionType.ALL_PEAKLISTS);

        DeconvolutionParameters deconvolutionParameters = setDeconvolutionParameters(peakListsSelection, peakResolver);

        DeconvolutionTask deconvolutionTask = new DeconvolutionTask(mZmineProject, peakListsSelection.getMatchingPeakLists()[0], deconvolutionParameters);
        deconvolutionTask.run();

        saveData(outputFile, peakListsSelection);


    }

    private static Options setOptions() {
        Options options = new Options();
        options.addOption(Option.builder("i").required().hasArg().longOpt("inputFile").desc("[required] Name or path of input file. File name must end with .MPL").build());
        options.addOption(Option.builder("o").required().hasArg().longOpt("outputFile").desc("[required] Name or path of output file. File name must end with .MPL").build());
        options.addOption(Option.builder("r").required().hasArg().longOpt("rawDataFile").desc("[required] Name or path of raw data file from previous step. File name must end with .CDF").build());
        options.addOption(Option.builder("snt").required(false).hasArg().longOpt("SNThreshold").desc("Signal to noise ratio threshold. [default 10.0]").build());
        options.addOption(Option.builder("mfh").required(false).hasArg().longOpt("minFeatureHeight").desc("Minimum height of a feature. Should be the same," +
                " or similar to, the value - min start intensity - set in the chromatogram building. [default 10.0]").build());
        options.addOption(Option.builder("at").required(false).hasArg().longOpt("areaThreshold").desc("This is a threshold for the maximum coefficient (inner product)." +
                "devided by the area under the curve of the feature. Filters out bad peaks. [default 110.0]").build());
        options.addOption(Option.builder("pdr").required(false).hasArgs().longOpt("peakDurationRange").desc("Range of acceptable peak lengths. [default from 0.00 to 10.00]").build());
        options.addOption(Option.builder("rtr").required(false).hasArgs().longOpt("RTRange").desc("Upper and lower bounds of retention times to be used for setting the wavelet scales." +
                "Choose a range that that similar to the range of peak widths expected to be found from the data. [default from 0.00 to 0.10]").build());
        options.addOption(Option.builder("iwe").required(false).longOpt("intensityWindowEstimator").desc("Intensity Window Estimator [as default]").build());
        options.addOption(Option.builder("w").required(false).longOpt("wavelet").desc("Wavelet Coefficient Estimator").build());
        options.addOption(Option.builder("pw").required(false).hasArg().longOpt("peakWidth").desc("Parameter used with Wavelet Coefficient Estimator. Signal to noise estimator window size determination. [default 3.0]").build());
        options.addOption(Option.builder("a").required(false).hasArg().longOpt("abs").desc("Parameter used with Wavelet Coefficient Estimator. Do you want to take the absolute value of the wavelet coefficients? [default true]").build());
        options.addOption(Option.builder("h").required(false).longOpt("help").build());

        return options;

    }

    private static ADAPDetectorParameters setADAPDetectorParameters(Boolean wavelet, Double peakWidth, Boolean abs,
                                                                    Double peakDurationRange1, Double peakDurationRange2,
                                                                    Double RTRange1, Double RTRange2, Double SNThreshold,
                                                                    Double minFeatureHeight, Double areaThreshold) {
        SNEstimatorChoice[] snEstimators = {new IntensityWindowsSNEstimator(),
                new WaveletCoefficientsSNEstimator()};
        MZmineProcessingStep<SNEstimatorChoice> snEstimatorChoiceMZmineProcessingStep;
        if (wavelet) {
            WaveletCoefficientsSNParameters waveletCoefficientsSNParameters = new WaveletCoefficientsSNParameters();
            waveletCoefficientsSNParameters.getParameter(WaveletCoefficientsSNParameters.HALF_WAVELET_WINDOW).setValue(peakWidth);
            waveletCoefficientsSNParameters.getParameter(WaveletCoefficientsSNParameters.ABS_WAV_COEFFS).setValue(abs);
            snEstimatorChoiceMZmineProcessingStep =
                    new MZmineProcessingStepImpl<>(snEstimators[1], waveletCoefficientsSNParameters);
        } else {
            IntensityWindowsSNParameters intensityWindowsSNParameters = new IntensityWindowsSNParameters();
            snEstimatorChoiceMZmineProcessingStep =
                    new MZmineProcessingStepImpl<>(snEstimators[0], intensityWindowsSNParameters);
        }

        ADAPDetectorParameters adapDetectorParameters = new ADAPDetectorParameters();
        adapDetectorParameters.getParameter(ADAPDetectorParameters.SN_ESTIMATORS).setValue(snEstimatorChoiceMZmineProcessingStep);

        adapDetectorParameters.getParameter(ADAPDetectorParameters.SN_THRESHOLD).setValue(SNThreshold);
        adapDetectorParameters.getParameter(ADAPDetectorParameters.MIN_FEAT_HEIGHT).setValue(minFeatureHeight);
        adapDetectorParameters.getParameter(ADAPDetectorParameters.COEF_AREA_THRESHOLD).setValue(areaThreshold);

        //working with range
        Double min = peakDurationRange1;
        Double max = peakDurationRange2;
        if (min > max) {
            min = peakDurationRange2;
            max = peakDurationRange1;
        }
        DoubleRangeParameter peakDurationRange = new DoubleRangeParameter("Peak duration range",
                "Range of acceptable peak lengths", MZmineCore.getConfiguration().getRTFormat(),
                Range.closed(min, max));
        adapDetectorParameters.getParameter(ADAPDetectorParameters.PEAK_DURATION)
                .setValue(peakDurationRange.getValue());


        min = RTRange1;
        max = RTRange2;
        if (min > max) {
            min = RTRange2;
            max = RTRange1;
        }
        DoubleRangeParameter RTRange = new DoubleRangeParameter("RT wavelet range",
                "Upper and lower bounds of retention times to be used for setting the wavelet scales. Choose a range that that simmilar to the range of peak widths expected to be found from the data.",
                MZmineCore.getConfiguration().getRTFormat(),
                true, Range.closed(min, max));
        adapDetectorParameters.getParameter(ADAPDetectorParameters.RT_FOR_CWT_SCALES_DURATION)
                .setValue(RTRange.getValue());
        return adapDetectorParameters;
    }

    private static DeconvolutionParameters setDeconvolutionParameters(PeakListsSelection peakListsSelection, MZmineProcessingStep<PeakResolver> peakResolver) {
        DeconvolutionParameters parameters = new DeconvolutionParameters();
        parameters.getParameter(DeconvolutionParameters.AUTO_REMOVE).setValue(false);
        parameters.getParameter(DeconvolutionParameters.mzRangeMSMS).setValue(false);
        parameters.getParameter(DeconvolutionParameters.RetentionTimeMSMS).setValue(false);
        parameters.getParameter(DeconvolutionParameters.SUFFIX).setValue("deconvoluted");
        parameters.getParameter(DeconvolutionParameters.PEAK_LISTS).setValue(peakListsSelection);
        parameters.getParameter(DeconvolutionParameters.PEAK_RESOLVER).setValue(peakResolver);

        return parameters;
    }

    private static void saveData(File outputFile, PeakListsSelection peakListsSelection) {

        XMLExportParameters xmlExportParameters = new XMLExportParameters();
        xmlExportParameters.getParameter(XMLExportParameters.filename).setValue(outputFile);
        xmlExportParameters.getParameter(XMLExportParameters.compression).setValue(false);
        xmlExportParameters.getParameter(XMLExportParameters.peakLists).setValue(peakListsSelection);

        XMLExportTask xmlExportTask = new XMLExportTask(xmlExportParameters);
        xmlExportTask.run();

    }


}

