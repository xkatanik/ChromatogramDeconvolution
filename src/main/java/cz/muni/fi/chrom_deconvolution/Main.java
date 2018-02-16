package cz.muni.fi.chrom_deconvolution;

import com.google.common.collect.Range;
import net.sf.mzmine.datamodel.MZmineProject;
import net.sf.mzmine.datamodel.PeakList;
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
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetectionParameters;
import net.sf.mzmine.modules.rawdatamethods.peakpicking.massdetection.MassDetector;
import net.sf.mzmine.modules.rawdatamethods.rawdataimport.fileformats.MzXMLReadTask;
import net.sf.mzmine.modules.rawdatamethods.rawdataimport.fileformats.NetCDFReadTask;
import net.sf.mzmine.parameters.ParameterSet;
import net.sf.mzmine.parameters.parametertypes.DoubleParameter;
import net.sf.mzmine.parameters.parametertypes.ModuleComboParameter;
import net.sf.mzmine.parameters.parametertypes.ranges.DoubleRangeParameter;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsSelection;
import net.sf.mzmine.parameters.parametertypes.selectors.PeakListsSelectionType;
import net.sf.mzmine.project.impl.MZmineProjectImpl;
import net.sf.mzmine.project.impl.ProjectManagerImpl;
import net.sf.mzmine.project.impl.RawDataFileImpl;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Logger;

/**
 * @author Kristian Katanik
 */
public class Main {

    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {

        Integer i = 0;
        String inputFileName = null;
        String rawData = null;
        String outputFileName = null;
        Double SNThreshold = 10.0;
        Double minFeatureHeight = 10.0;
        Double areaThreshold = 110.0;
        Double peakDurationRange1 = 0.00;
        Double peakDurationRange2 = 10.00;
        Double RTRange1 =0.00;
        Double RTRange2 = 0.10;
        //For Wavelet SN estimator
        Boolean wavelet = false;
        Double peakWidth = 3.0;
        Boolean abs = true;
        while(i < args.length){
            switch (args[i]){
                case "-inputFile":
                    inputFileName = args[i+1];
                    i += 2;
                    break;
                case "-outputFile":
                    outputFileName = args[i+1];
                    i += 2;
                    break;
                case "-rawDataFile":
                    rawData = args[i+1];
                    i += 2;
                    break;
                case "-SNThreshold":
                    try {
                        SNThreshold = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Wrong format of -SNThreshold parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-minFeatureHeight":
                    try {
                        minFeatureHeight = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Wrong format of -minFeatureHeight parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-areaThreshold":
                    try {
                        areaThreshold = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Wrong format of -areaThreshold parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-peakDurationRange":
                    try {
                        peakDurationRange1 = Double.parseDouble(args[i+1]);
                        peakDurationRange2 = Double.parseDouble(args[i+2]);
                    } catch (Exception e){
                        System.err.println("Wrong format of -peakDuration parameters. You have to set 2 values (from, to).");
                        return;
                    }
                    i += 3;
                    break;
                case "-RTRange":
                    try {
                        RTRange1 = Double.parseDouble(args[i+1]);
                        RTRange2 = Double.parseDouble(args[i+2]);
                    } catch (Exception e){
                        System.err.println("Wrong format of -RTRange parameters. You have to set 2 values (from, to).");
                        return;
                    }
                    i += 3;
                    break;
                case "-wavelet":
                    wavelet = true;
                    i++;
                    break;
                case "-peakWidth":
                    try {
                        peakWidth = Double.parseDouble(args[i+1]);
                    } catch (Exception e){
                        System.err.println("Wrong format of -peakWidth parameter.");
                        return;
                    }
                    i += 2;
                    break;
                case "-abs":
                    if(args[i+1] == "false" || args[i+1] == "f"){
                        abs = false;
                    }
                    i++;
                    break;
                case "-intensity":
                    i++;
                    break;
                case "-help":
                    System.out.println("Chromatogram deconvolution.\n" +
                            "This module separates each detected chromatogram into individual peaks.\n"+
                            "\n" +
                            "Required parameters:\n" +
                            "\t-inputFile | Name or path of input file after ADAP Chromatogram builder, ending with .MPL\n" +
                            "\t-outputFile | Name or path of output file. File name must end with .MPL\n" +
                            "\t-rawDataFile | Name or path of input file after Mass detection, ending with .CDF\n" +
                            "\n" +
                            "Optional parameters:\n" +
                            "\t-SNThreshold | Signal to noise ratio threshold.\n" +
                            "\t\t[default 10.0]\n" +
                            "\t-minFeatureHeight | Minimum height of a feature. Should be the same,\n" +
                            "\t\t or similar to, the value - min start intensity - set in the chromatogram building.\n" +
                            "\t\t[default 10.0]\n" +
                            "\t-areaThreshold | This is a threshold for the maximum coefficient (inner product)\n" +
                            "\t\tdevided by the area under the curve of the feature. Filters out bad peaks.\n" +
                            "\t\t[default 110.0]\n" +
                            "\t-peakDurationRange | Range of acceptable peak lengths.\n" +
                            "\t\t[default from 0.00 to 10.00]\n" +
                            "\t-RTRange | Upper and lower bounds of retention times to be used for setting the wavelet scales.\n" +
                            "\t\tChoose a range that that similar to the range of peak widths expected to be found from the data.\n" +
                            "\t\t[default from 0.00 to 0.10]" +
                            "\n" +
                            "\tS/N estimators:\n" +
                            "\t\tIntensity Window Estimator [as default]\n" +
                            "\t\t-wavelet | Wavelet Coefficient Estimator\n" +
                            "\t\t\t-peakWidth | Signal to noise estimator window size determination.\n" +
                            "\t\t\t\t[default 3.0]\n" +
                            "\t\t\t-abs | Do you want to take the absolute value of the wavelet coefficients?\n" +
                            "\t\t\t\t[default true]\n" +
                            "\n");
                    return;
                default:
                    i++;
                    break;
            }
        }

        //Reading 2 input files

        File inputFile;
        try {
            inputFile = new File(inputFileName);
        } catch (Exception e) {
            System.out.println("Unable to load input file.");
            return;
        }

        File rawInputFile;
        try {
            rawInputFile = new File(rawData);
        } catch (Exception e) {
            System.out.println("Unable to load raw file.");
            return;
        }

        File outputFile;
        try {
            outputFile = new File(outputFileName);
        } catch(Exception e){
            System.out.println("Unable to create/load output file.");
            return;
        }

        if(!inputFile.exists() || inputFile.isDirectory() || !rawInputFile.exists() || rawInputFile.isDirectory()){
            System.err.println("Unable to load input/raw file.");
            return;
        }

        final MZmineProject mZmineProject = new MZmineProjectImpl();
        RawDataFileImpl rawDataFile = null;
        try {
            rawDataFile = new RawDataFileImpl(inputFile.getName());
        } catch (IOException e) {
            System.err.println("Cant load input data file.");
            return;
        }

        //code for raw data
        RawDataFileImpl rawDataFile2 = null;
        try {
            rawDataFile2 = new RawDataFileImpl(rawInputFile.getName());
        } catch (IOException e) {
            System.err.println("Unable to open raw data file.");
            return;
        }

        NetCDFReadTask netCDFReadTask = new NetCDFReadTask(mZmineProject,rawInputFile,rawDataFile2);
        netCDFReadTask.run();
        mZmineProject.addFile(rawDataFile2);


        XMLImportParameters xmlImportParameters = new XMLImportParameters();
        xmlImportParameters.getParameter(XMLImportParameters.filename).setValue(inputFile);
        XMLImportTask xmlImportTask = new XMLImportTask(mZmineProject,xmlImportParameters);
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

        PeakListsSelection peakListsSelection = new PeakListsSelection();
        peakListsSelection.setSelectionType(PeakListsSelectionType.ALL_PEAKLISTS);


        //setting parameters
        SNEstimatorChoice[] SNESTIMATORS ={ new IntensityWindowsSNEstimator(),
                new WaveletCoefficientsSNEstimator()};
        MZmineProcessingStep<SNEstimatorChoice> snEstimatorChoiceMZmineProcessingStep;
        if(wavelet){
            WaveletCoefficientsSNParameters waveletCoefficientsSNParameters = new WaveletCoefficientsSNParameters();
            waveletCoefficientsSNParameters.getParameter(WaveletCoefficientsSNParameters.HALF_WAVELET_WINDOW).setValue(peakWidth);
            waveletCoefficientsSNParameters.getParameter(WaveletCoefficientsSNParameters.ABS_WAV_COEFFS).setValue(abs);
            snEstimatorChoiceMZmineProcessingStep =
                    new MZmineProcessingStepImpl<SNEstimatorChoice>(SNESTIMATORS[1],waveletCoefficientsSNParameters);
        } else{
            IntensityWindowsSNParameters intensityWindowsSNParameters = new IntensityWindowsSNParameters();
             snEstimatorChoiceMZmineProcessingStep =
                    new MZmineProcessingStepImpl<SNEstimatorChoice>(SNESTIMATORS[0],intensityWindowsSNParameters);
        }

        ADAPDetectorParameters adapDetectorParameters = new ADAPDetectorParameters();
        adapDetectorParameters.getParameter(ADAPDetectorParameters.SN_ESTIMATORS).setValue(snEstimatorChoiceMZmineProcessingStep);

        adapDetectorParameters.getParameter(ADAPDetectorParameters.SN_THRESHOLD).setValue(SNThreshold);
        adapDetectorParameters.getParameter(ADAPDetectorParameters.MIN_FEAT_HEIGHT).setValue(minFeatureHeight);
        adapDetectorParameters.getParameter(ADAPDetectorParameters.COEF_AREA_THRESHOLD).setValue(areaThreshold);

        //working with range
        Double min = peakDurationRange1;
        Double max = peakDurationRange2;
        if(min > max){
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
        if(min > max){
            min = RTRange2;
            max = RTRange1;
        }
        DoubleRangeParameter RTRange = new DoubleRangeParameter("RT wavelet range",
                "Upper and lower bounds of retention times to be used for setting the wavelet scales. Choose a range that that simmilar to the range of peak widths expected to be found from the data.",
                MZmineCore.getConfiguration().getRTFormat(),
                true, Range.closed(min, max));
        adapDetectorParameters.getParameter(ADAPDetectorParameters.RT_FOR_CWT_SCALES_DURATION)
                .setValue(RTRange.getValue());


        ADAPDetector adapDetector = new ADAPDetector();

        MZmineProcessingStep<PeakResolver> peakResolver = new MZmineProcessingStepImpl<PeakResolver>(adapDetector,adapDetectorParameters);


        PeakList[]  peakList = mZmineProject.getPeakLists(rawDataFile);

        DeconvolutionParameters parameters = new DeconvolutionParameters();
        parameters.getParameter(DeconvolutionParameters.AUTO_REMOVE).setValue(false);
        parameters.getParameter(DeconvolutionParameters.mzRangeMSMS).setValue(false);
        parameters.getParameter(DeconvolutionParameters.RetentionTimeMSMS).setValue(false);
        parameters.getParameter(DeconvolutionParameters.SUFFIX).setValue("deconvoluted");
        parameters.getParameter(DeconvolutionParameters.PEAK_LISTS).setValue(peakListsSelection);
        parameters.getParameter(DeconvolutionParameters.PEAK_RESOLVER).setValue(peakResolver);

        DeconvolutionTask deconvolutionTask = new DeconvolutionTask(mZmineProject,peakListsSelection.getMatchingPeakLists()[0],parameters);
        deconvolutionTask.run();


        XMLExportParameters xmlExportParameters = new XMLExportParameters();
        xmlExportParameters.getParameter(XMLExportParameters.filename).setValue(outputFile);
        xmlExportParameters.getParameter(XMLExportParameters.compression).setValue(false);
        xmlExportParameters.getParameter(XMLExportParameters.peakLists).setValue(peakListsSelection);

        XMLExportTask xmlExportTask = new XMLExportTask(xmlExportParameters);
        xmlExportTask.run();



    }
}
