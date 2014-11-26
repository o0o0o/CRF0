/** Segment.java
 * 
 * @author Sunita Sarawagi
 * @since 1.0
 * @version 1.3
 */
package iitb.Segment;
import iitb.BSegment.BFeatureGenImpl;
import iitb.BSegmentCRF.BSegmentCRF;
import iitb.CRF.CRF;
import iitb.CRF.FeatureGenerator;
import iitb.CRF.NestedCRF;
import iitb.CRF.SegmentDataSequence;
import iitb.CRF.Segmentation;
import iitb.CRF.Util;
import iitb.Model.FeatureGenImpl;
import iitb.Model.NestedFeatureGenImpl;
import iitb.Utils.Options;

import java.io.File;
import java.io.FileInputStream;
import java.util.StringTokenizer;
import java.util.Vector;

public class Segment {
    String inName;
    String outDir;
    String baseDir="";
    int nlabels;

    String delimit=" \t"; // used to define token boundaries
    String tagDelimit="|"; // separator between tokens and tag number
    String impDelimit=""; // delimiters to be retained for tagging
    String groupDelimit=null;

    boolean confuseSet[]=null;
    boolean validate = false; 
    String mapTagString = null;
    String smoothType = "";

    String modelArgs = "";
    String featureArgs = "";
    String modelGraphType = "naive";

    LabelMap labelMap;
    Options options;

    CRF crfModel;
    FeatureGenImpl featureGen;
    public FeatureGenerator featureGenerator() {return featureGen;}

    public static void main(String argv[]) throws Exception {
        argv[0] = "all";
        argv[1] = "-f";
        argv[2] = "samples/us50.conf";
        if (argv.length < 3) {
            System.out.println("Usage: java iitb.Segment.Segment <train|test|calc|all> -f <conf-file>");
            return;
        }
        // claim a instance segment of it's own class Segment.
        Segment segment = new Segment();
        // the argv passed to the main program will be parsed by the Segment's parseConf method.
        // the change should be saved inside the segment instance. so the instantce of Segment class 
        // has comprehensive information/parameter.
        segment.parseConf(argv);
        // there are three phases in total.
        if (argv[0].toLowerCase().equals("all")) {
            // the result of training is the numerical structure of the data.
            segment.train();
            // still unclear about the doTest() function.
            segment.doTest();
            // do calculation about the recall rate, some statistics.
            segment.calc();
        } 
        if (argv[0].toLowerCase().equals("train")) {
            segment.train();
        } 
        if (argv[0].toLowerCase().equals("test")) {
            segment.test();
        }
        if (argv[0].toLowerCase().equals("calc")) {
            segment.calc();
        }
    }

    // parse the arguments passed into the main program, save into options(of type Option).
    public void parseConf(String argv[]) throws Exception {
        options = new Options();
        int startIndex = 1;// unused.
        if ((argv.length >= 2) && argv[1].toLowerCase().equals("-f")) {
            // options read from some file specified by argv[2]
            options.load(new FileInputStream(argv[2]));
        }
        // add the whole argv into options?
        options.add(3, argv);
        //process upon the infomation obtained into options.
        // so processArgs is actually processing the instance options.
        processArgs();
    }

    // dig out information from the options field in ways of .getMandatoryProperty()
    // options are read from some file specified in argv.
    public void processArgs() throws Exception {
        String value = null;
        if ((value = options.getMandatoryProperty("numlabels")) != null) {
            nlabels=Integer.parseInt(value);
        }
        if ((value = options.getProperty("binary")) != null) {
            nlabels = 2;
            labelMap = new BinaryLabelMap(options.getInt("binary"));
        } else {
            labelMap = new LabelMap();
        }
        if ((value = options.getMandatoryProperty("inname")) != null) {
            inName=new String(value);
        }
        if ((value = options.getMandatoryProperty("outdir")) != null) {
            outDir=new String(value);
        }
        if ((value = options.getProperty("basedir")) != null) {
            baseDir=new String(value);
        }
        if ((value = options.getProperty("tagdelimit")) != null) {
            tagDelimit=new String(value);
        }
        // delimiters that will be ignored.
        if ((value = options.getProperty("delimit")) != null) {
            delimit=new String(value);
        }
        if ((value = options.getProperty("impdelimit")) != null) {
            impDelimit=new String(value);
        }
        if ((value = options.getProperty("groupdelimit")) != null) {
            groupDelimit=value;
        }
        if ((value = options.getProperty("confusion")) != null) {
            StringTokenizer confuse=new StringTokenizer(value,", ");
            int confuseSize=confuse.countTokens();
            confuseSet=new boolean[nlabels+1];
            for(int i=0 ; i<confuseSize ; i++) {
                confuseSet[Integer.parseInt(confuse.nextToken())]=true;
            }
        }
        if ((value = options.getProperty("map-tags")) != null) {
            mapTagString = value;
        }
        if ((value = options.getProperty("validate")) != null) {
            validate = true;
        }
        if ((value = options.getProperty("model-args")) != null) {
            modelArgs = value;
            System.out.println(modelArgs);
        }
        if ((value = options.getProperty("feature-args")) != null) {
            featureArgs = value;
        }
        if ((value = options.getProperty("modelGraph")) != null) {
            modelGraphType = value;
        }
    }
    // allocate a model, but what is a model here?
    // how to allocate the memory?no
    // the purpose of allocModel is to populate field: featureGen and crfModel.
    void  allocModel() throws Exception {
        // add any code related to dependency/consistency amongst parameter
        // values here..
        // make sure the model type, by check the modelGraphType
        // if the model is semi-markov, then all the following decide the detail modelArgs.
        
        //--------------// if semi-Markov, two choices, BFeature or NestedFeature.
        // if not, use the basic version.
        if (modelGraphType.equals("semi-markov")) {
            if (options.getInt("debugLvl") > 1) {
                Util.printDbg("Creating semi-markov model");
            }
            if (modelArgs.equals("bcrf")) {
                // featureGen is assigned by a BFeatureGenImpl
                // so it is actually use a BFeatureGenImpl as a FeatureGenImpl, so what is a BFeature?
                // the BFeature corresponds to bcrf: does it mean BSegmentCRF?
                // if bcrf, everything becomes a special edition of (FeatureGen->BFeatureGenImpl, CRF->BSegmentCRF, etc.)
                BFeatureGenImpl fgen = new BFeatureGenImpl(modelGraphType,nlabels,options);
                featureGen = fgen;
                crfModel = new BSegmentCRF(featureGen.numStates(),fgen,options);
            } else {
                // if not bcrf, use NestedFeatureGenImpl instead(the logic here is get BFeature or NestedFeature)
                NestedFeatureGenImpl nfgen = new NestedFeatureGenImpl(nlabels,options);
                featureGen = nfgen;
                crfModel = new NestedCRF(featureGen.numStates(),nfgen,options);
            }
        } else // if not semi-markov
        {
            // not semi-markov, use the traditional/classical version(FeatureGenImpl & CRF)
            featureGen = new FeatureGenImpl(modelGraphType, nlabels);
            crfModel=new CRF(featureGen.numStates(),featureGen,options);
        }
    }
    // this is an inner class inside the Segment Class
    // it is a SegmentDataSequence and a Segmentation
    // what it stands for?
    class TestRecord implements SegmentDataSequence, Segmentation {
        /**
		 * 
		 */
        private static final long serialVersionUID = -9126147224366724551L;
        // There are two arrays: String seg[] and int path[].
        String seq[];
        int path[]; // path is a int array with the length of seq.
        // use a string array to construct a new TestRecord
        TestRecord(String s[]) {
            seq=s;
            path=new int[seq.length];
        }
        void init(String s[]) {
            seq = s;
            // if path is old, update it with a newly allocated int array
            if ((path == null) || (path.length < seq.length)) {
                path = new int[seq.length];
            }
        }
        // y is more about the int array: path[]
        public void set_y(int i, int l) {path[i] = l;} // not applicable for training data.
        public int y(int i) {return path[i];} //similar to a get_y func
        
        public int length() {return seq.length;}
        
        // what's inside the seq is String, but will be returned as Object.
        public Object x(int i) {return seq[i];}
        /* (non-Javadoc)
         * @see iitb.CRF.SegmentDataSequence#getSegmentEnd(int)
         */
        // find the segmentEnd from a segmentStart.
        public int getSegmentEnd(int segmentStart) {
            // y(segmentStart) = path[segmentStart]
            if ((segmentStart > 0) && (y(segmentStart) == y(segmentStart-1)))
                return -1;// should be something wrong.
            for (int i = segmentStart+1; i < length(); i++) {
                if (y(i)!= y(segmentStart))
                    return i-1;
            }
            return length()-1;
        }
        /* (non-Javadoc)
         * @see iitb.CRF.SegmentDataSequence#setSegment(int, int, int)
         */
        // setSegment means each position has a segment value.
        public void setSegment(int segmentStart, int segmentEnd, int y) {
            for (int i = segmentStart; i <= segmentEnd; i++)
                // from segmentStart to segmentEnd, all positions are set to value y.
                set_y(i,y);
        }
        //new unsupported functions.
        public int getSegmentId(int offset) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }
        public int numSegments() {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }
        public int segmentEnd(int segmentNum) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }
        public int segmentLabel(int segmentNum) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }
        public int segmentStart(int segmentNum) {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException();
        }
    };// end of inner Class TestRecord


    // return an int array path(with or without modification?)
    public int[] segment(TestRecord testRecord, int[] groupedToks, String collect[]) {
        // each testRecord has a length. every seq counts 1 in the length.
        // each testRecord.seq[i] get preprocessed by AlphaNumericaPreprocessor.preprocess
        // and store back to itself.
        for (int i = 0; i < testRecord.length(); i++)
            testRecord.seq[i] = AlphaNumericPreprocessor.preprocess(testRecord.seq[i]);
        // a Model(of CRF type) can apply some TestRecord. (model applies some TestRecord)
        crfModel.apply(testRecord);
        // the FeatureGenerator also deal with the TestRecord by doing mapStatesToLabels.(states to labels)
        featureGen.mapStatesToLabels(testRecord);
        // TestRecord has an int array called path. get a local reference here.
        int path[] = testRecord.path;
        // nlabels should be the number of all the labels. each label stands for a state.
        // collect is a String array, initialize all to null String
        for (int i = 0; i < nlabels; i++)
            collect[i] = null;
        for(int i=0 ; i<testRecord.length() ; i++) {
            // System.out.println(testRecord.seq[i] + " " + path[i]);
            int snew=path[i];
            // modify collect[snew], for each path[i](snew)
            if (snew >= 0) {
                if (collect[snew]==null) {
                    collect[snew]=testRecord.seq[i];
                } else {
                    collect[snew]=collect[snew]+" "+testRecord.seq[i];
                }
            }
        }
        return path;
    }
    // this method train() is a major one in the Segment Class
    public void train() throws Exception {
        // the DataCruncher.createRaw method create some raw file named as "xxx.train", specified by tagDelimit
        DataCruncher.createRaw(baseDir+"/data/"+inName+"/"+inName+".train",tagDelimit);
        // create necessary directories.
        File dir=new File(baseDir+"/learntModels/"+outDir);
        dir.mkdirs();
        // a new type involved: TrainData(not TestRecord in the previous method(segment))
        // The DataCruncher read the tagged file by invoking the readTagged() method, 
        // and return an instance of TrainData.
        TrainData trainData = DataCruncher.readTagged(nlabels,baseDir+"/data/"+inName+"/"+inName+".train",baseDir+"/data/"+inName+"/"+inName+".train",delimit,tagDelimit,impDelimit,labelMap);
        // the trainData get preprocessed, provided with a nlabel parameter.
        // it is good to figure out what the preprocess method does. must be something about states and labels.
        AlphaNumericPreprocessor.preprocess(trainData,nlabels);
        // so far get all the data in trainData(preprocessed) to passed to new model. 
        // allocate the model, access the local field of the Segment Class.
        allocModel();
        // the featureGen:  feature generator? is a FeatureGenImpl type Class(feature generator implementation).
        // we pass the trainData into the feature generator, to build the features?
        // so if we want to modify the feature set, we need to modify the train method of the FeatureGenImpl Class.
        // I guess the train method tries to train the features first.(for weights)??
        featureGen.train(trainData);
        // the results of crfModel.train is a double array, as featureWts: feature weights. 
        // Are they the only results?
        double featureWts[] = crfModel.train(trainData);
        if (options.getInt("debugLvl") > 1) {
            Util.printDbg("Training done");
        }
        // assume the model itself is the crfModel of type CRF, 
        // the crfModel is a member field in this Segment Class. 
        // hard to understand why this class has the name of Segment. 
        crfModel.write(baseDir+"/learntModels/"+outDir+"/crf");
        featureGen.write(baseDir+"/learntModels/"+outDir+"/features");
        if (options.getInt("debugLvl") > 1) {
            Util.printDbg("Writing model to "+ baseDir+"/learntModels/"+outDir+"/crf");
        }
        if (options.getProperty("showModel") != null) {
            featureGen.displayModel(featureWts);
        }
    }

    public void test() throws Exception {
        allocModel();
        // populate the key fields of featureGen and crfModel.
        featureGen.read(baseDir+"/learntModels/"+outDir+"/features");
        crfModel.read(baseDir+"/learntModels/"+outDir+"/crf");
        // here comes the real function: doTest().
        doTest();
    }
    public void doTest() throws Exception {
        File dir=new File(baseDir+"/out/"+outDir);
        dir.mkdirs();
        // Now comes the new data type: TestData
        TestData testData = new TestData(baseDir+"/data/"+inName+"/"+inName+".test",delimit,impDelimit,groupDelimit);
        // first get the testData, and then get the instance of TestDataWrite Class.
        TestDataWrite tdw = new TestDataWrite(baseDir+"/out/"+outDir+"/"+inName+".test",baseDir+"/data/"+inName+"/"+inName+".test",delimit,tagDelimit,impDelimit,labelMap);

        // now the string array collect reappears.
        String collect[] = new String[nlabels];
        // use the collect string array to initialize the TestRecord.
        TestRecord testRecord = new TestRecord(collect);
        // one record is a String sequence called seq[] here, fetched by testData.nextRecord().
        for(String seq[] = testData.nextRecord();  seq != null; 
        seq = testData.nextRecord()) {
            // testRecord can init with some String array seq[]
            testRecord.init(seq);
            if (options.getInt("debugLvl") > 1) {
                Util.printDbg("Invoking segment on " + seq);
            }
            // now that the segment method can be invoked here, it means that
            // the segment method is universal, could be invoked by other tasks
            // in the form of (testRecord, groupedTokens(), string[] collect)
            int path[] = segment(testRecord, testData.groupedTokens(), collect);
            // then the TestDataWrite tdw could be written using hte writeRecord method.
            tdw.writeRecord(path,testRecord.length());
        }
        // close the TestDataWrite and finish doTest()
        tdw.close();
    }
    // initialize new field(taggedData) of TrainData type.
    TrainData taggedData = null;
    // extract labels from TrainRecord type instance
    int[] allLabels(TrainRecord tr) {
        // the number of labels is the same with the length of TrainRecord
        int[] labs = new int[tr.length()];
        for (int i = 0; i < labs.length; i++)
            // invoke the y() method, getting the label from every position/record in the TrainRecord tr
            labs[i] = tr.y(i);
        return labs;
    }
    // convert an Object array to a single String by doing concating.
    String arrayToString(Object[] ar) {
        String st = "";
        for (int i = 0; i < ar.length; i++)
            st += (ar[i] + " ");
        return st;
    }
    // one of the major methods around here.
    public void calc() throws Exception {
        Vector<String[]> s = new Vector<String[]>();
        // TrainData manual, from tagged file.
        TrainData tdMan = DataCruncher.readTagged(nlabels,baseDir+"/data/"+inName+"/"+inName+".test",baseDir+"/data/"+inName+"/"+inName+".test",delimit,tagDelimit,impDelimit,labelMap);
        // TrainData auto, from tagged file.
        TrainData tdAuto = DataCruncher.readTagged(nlabels,baseDir+"/out/"+outDir+"/"+inName+".test",baseDir+"/data/"+inName+"/"+inName+".test",delimit,tagDelimit,impDelimit,labelMap);
        // read raw data into Vector<String[]> s?
        DataCruncher.readRaw(s,baseDir+"/data/"+inName+"/"+inName+".test","","");
        int len=tdAuto.size();
        // the followings are three int arrays with the same length.
        // true position? length relates to nlabels
        int truePos[]=new int[nlabels+1];
        // total marked position? length relates to nlabels
        int totalMarkedPos[]=new int[nlabels+1];
        // total position? length relates to nlabels
        int totalPos[]=new int[nlabels+1]; 
        /// confuse matrix
        int confuseMatrix[][]=new int[nlabels][nlabels];
        boolean printDetails = (options.getInt("debugLvl") > 0);
        // self correction. so called Sanity Check.
        if (tdAuto.size() != tdMan.size()) {
            // Sanity Check
            System.out.println("Length Mismatch - Raw: "+len+" Auto: "+tdAuto.size()+" Man: "+tdMan.size());
        }

        // len is the length of TrainData Auto's size.
        // One TrainData has many TrainRecords. get been fetched by invoking TrainData.nextRecord().
        for(int i=0 ; i<len ; i++) { // for every record
            // s is where all the information read from file is stored.
            String raw[]=(String [])(s.get(i));
            TrainRecord trMan = tdMan.nextRecord();
            TrainRecord trAuto = tdAuto.nextRecord();
            //extract all the labels from trainRecord seperatedly.
            int tokenMan[]=allLabels(trMan);
            int tokenAuto[]=allLabels(trAuto);

            if (tokenMan.length!=tokenAuto.length) {
                // Sanity Check
                System.out.println("Length Mismatch - Manual: "+tokenMan.length+" Auto: "+tokenAuto.length);
                //			continue;
            }
            // remove invalid tagging.
            boolean invalidMatch = false;
            // tlen is size of all tokens(similar to number of labels?)
            int tlen=tokenMan.length;
            for (int j = 0; j < tlen; j++) {
                if (printDetails) System.err.println(tokenMan[j] + " " + tokenAuto[j]);
                if (tokenAuto[j] < 0) {
                    invalidMatch = true;
                    break;
                }
            }
            // if invalid match, exit directly.
            if (invalidMatch) {
                if (printDetails) System.err.println("No valid path");
                continue;
            }
            int correctTokens=0;
            // count the correct tokens.
            for(int j=0 ; j<tlen ; j++) {
                totalMarkedPos[tokenAuto[j]]++;
                totalMarkedPos[nlabels]++;
                totalPos[tokenMan[j]]++;
                totalPos[nlabels]++;
                confuseMatrix[tokenMan[j]][tokenAuto[j]]++;
                if (tokenAuto[j]==tokenMan[j]) {
                    correctTokens++;
                    truePos[tokenMan[j]]++;
                    truePos[nlabels]++;
                }
            }
            if (printDetails) System.err.println("Stats: "+correctTokens+" "+(tlen));
            int rlen=raw.length;
            for(int j=0 ; j<rlen ; j++) {
                if (printDetails) System.err.print(raw[j]+" ");
            }
            if (printDetails) System.err.println();
            for(int j=0 ; j<nlabels ; j++) {			    
                String mstr = "";
                for (int k = 0;  k < trMan.numSegments(j);k++)
                    mstr += arrayToString(trMan.tokens(j,k));
                String astr = "";
                for (int k = 0;  k < trAuto.numSegments(j);k++)
                    astr += arrayToString(trAuto.tokens(j,k));

                if (! mstr.equalsIgnoreCase(astr))
                    if (printDetails) System.err.print("W");
                if (printDetails) System.err.println(j+": "+ mstr+" : "+astr);
            }
            if (printDetails) System.err.println();
        }

        if (confuseSet!=null) {
            System.out.println("Confusion Matrix:");
            System.out.print("M\\A");
            for(int i=0 ; i<nlabels ; i++) {
                if (confuseSet[i]) {
                    System.out.print("\t"+(i));
                }
            }
            System.out.println();
            for(int i=0 ; i<nlabels ; i++) {
                if (confuseSet[i]) {
                    System.out.print(i);
                    for(int j=0 ; j<nlabels ; j++) {
                        if (confuseSet[j]) {
                            System.out.print("\t"+confuseMatrix[i][j]);
                        }
                    }
                    System.out.println();
                }
            }
        }
        System.out.println("\n\nCalculations:");
        System.out.println();
        System.out.println("Label\tTrue+\tMarked+\tActual+\tPrec.\tRecall\tF1");
        double prec,recall;
        for(int i=0 ; i<nlabels ; i++) {
            prec=(totalMarkedPos[i]==0)?0:((double)(truePos[i]*100000/totalMarkedPos[i]))/1000;
            recall=(totalPos[i]==0)?0:((double)(truePos[i]*100000/totalPos[i]))/1000;
            System.out.println((i)+":\t"+truePos[i]+"\t"+totalMarkedPos[i]+"\t"+totalPos[i]+"\t"+prec+"\t"+recall+"\t"+2*prec*recall/(prec+recall));
        }
        System.out.println("---------------------------------------------------------");
        prec=(totalMarkedPos[nlabels]==0)?0:((double)(truePos[nlabels]*100000/totalMarkedPos[nlabels]))/1000;
        recall=(totalPos[nlabels]==0)?0:((double)(truePos[nlabels]*100000/totalPos[nlabels]))/1000;
        System.out.println("Ov:\t"+truePos[nlabels]+"\t"+totalMarkedPos[nlabels]+"\t"+totalPos[nlabels]+"\t"+prec+"\t"+recall+"\t"+2*prec*recall/(prec+recall));

    }
};
