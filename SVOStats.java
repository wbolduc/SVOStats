/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package svostats;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.util.Pair;
import multicorenlp.BWord;
import multicorenlp.SVO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FilenameUtils;

/**
 *
 * @author wbolduc
 */
public class SVOStats {
    static Comparator countComparator = new Comparator<Entry<BWord,Integer>>() {
        @Override
        public int compare(Entry<BWord, Integer> o1, Entry<BWord, Integer> o2) {
            return o1.getValue() - o2.getValue();
        }
    };

    public static void main(String[] args) throws IOException {
        //reading arguments
        if(args.length == 0)
        {
            System.out.println("No input file given");
            System.exit(0);
        }
        
        String inFile = args[0];
        inFile = FilenameUtils.normalize(inFile);
        if(inFile == null)
        {
            System.out.println("Not a valid file path");
            System.exit(0);
        }
        if(FilenameUtils.getExtension(inFile).equals("csv") != true)
        {
            System.out.println("Input file must be csv");
            System.exit(0);
        }
        
        String pathNoExtension = FilenameUtils.getFullPath(inFile) + FilenameUtils.getBaseName(inFile);
        
        
        System.out.println("Loading " + inFile);
        ArrayList<SVO> svos = loadAllSVOsFromCSV(inFile);

        
        System.out.println("Getting frequencies"); 
        //accumulate subjects, verbs, and objects
        HashMap<BWord, Integer> subjectCounts   = new HashMap<>();
        HashMap<BWord, Integer> verbCounts      = new HashMap<>();
        HashMap<BWord, Integer> objectCounts    = new HashMap<>();
        HashMap<SVO, ArrayList<SVO>> svoGroups = new HashMap<>();
        
        svos.forEach(svo -> {
            //subjects
            Integer count = subjectCounts.get(svo.getSubject());
            if(count == null)
                subjectCounts.put(svo.getSubject(), 1);
            else
                subjectCounts.replace(svo.getSubject(), count+1);
            
            //verbs
            count = verbCounts.get(svo.getVerb());
            if(count == null)
                verbCounts.put(svo.getVerb(), 1);
            else
                verbCounts.replace(svo.getVerb(), count+1);
            
            //objects
            count = objectCounts.get(svo.getObject());
            if(count == null)
                objectCounts.put(svo.getObject(), 1);
            else
                objectCounts.replace(svo.getObject(), count+1);
            
            ArrayList<SVO> group = svoGroups.get(svo);
            if(group == null)
                svoGroups.put(svo, new ArrayList<>(Arrays.asList(svo)));
            else
                group.add(svo);
        });
        
        //compress groups to a single sentiment value
        //each entry is of the form Pair<Representative SVO, <some data, maybe just a count, maybe a sentiment>>
        ArrayList<Pair<SVO, Pair<Double, Integer>>> svoSents = new ArrayList<>();
        svoGroups.entrySet().forEach(group -> svoSents.add(new Pair(group.getKey(), compressGroup(group.getValue())))); //to change the compression scheme, change the function (defined below
        
        
        System.out.println("Sorting Subjects");
        ArrayList<Entry<BWord, Integer>> subjectFreqs = mapToSortedList(subjectCounts);
        
        System.out.println("Sorting Verbs");
        ArrayList<Entry<BWord, Integer>> verbFreqs = mapToSortedList(verbCounts);
        
        System.out.println("Sorting Objects");
        ArrayList<Entry<BWord, Integer>> objectFreqs = mapToSortedList(objectCounts);
        
        
        //Storing all the subjects that appear in svos ordered by frequency count
        System.out.println("Writing sorted subjects");
        storeCountList(subjectFreqs,"subject",pathNoExtension + "-SubjectFreqs.csv");
        
        //Storing all the verbs that appear in svos ordered by frequency count
        System.out.println("Writing sorted verbs");
        storeCountList(verbFreqs,"verb",pathNoExtension + "-VerbFreqs.csv");

        //Storing all the objects that appear in svos ordered by frequency count
        System.out.println("Writing sorted objects");
        storeCountList(objectFreqs,"objects", pathNoExtension + "-ObjectFreqs.csv");

        //writing a compressed SVO file //Note this should probably be two programs
        System.out.println("Writing sentiment for svo groups");
        CSVPrinter printer = new CSVPrinter(new BufferedWriter(new FileWriter(pathNoExtension + "-SVOsGroupedWithSentiment.csv")),
                                    CSVFormat.DEFAULT.withHeader(   "subject",
                                                                    "subjectNegated",
                                                                    "verb",
                                                                    "verbNegated",
                                                                    "object",
                                                                    "objectNegated",
                                                                    "sentiment",
                                                                    "count"));
        svoSents.forEach(tuple -> {
            try {
                SVO svo = tuple.getKey();
                BWord subject = svo.getSubject();
                BWord verb = svo.getVerb();
                BWord object = svo.getObject();
                printer.printRecord(subject.word,
                                    subject.negated,
                                    verb.word,
                                    verb.negated,
                                    object.word,
                                    object.negated,
                                    tuple.getValue().getKey(),      //sentiment for this group
                                    tuple.getValue().getValue());   //count of this group
            } catch (IOException ex) {
                Logger.getLogger(SVOStats.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        printer.close();
        System.out.println("Done");
    }
    
    public static Pair<Double, Integer> compressGroup(ArrayList<SVO> group)
    {
        //FILL THIS WITH THE REQUIRED SENTIMENT AND WEIGHTING
        double sum = 0;
        for(SVO svo : group)
        {
            sum += svo.getSentiment();
        }
        return new Pair<Double,Integer>(sum/group.size(), group.size());
    }
    
    public static ArrayList<Entry<BWord, Integer>> mapToSortedList(HashMap<BWord, Integer> wordCounts)
    {
        ArrayList<Entry<BWord, Integer>> wordFreqs = new ArrayList<>(wordCounts.entrySet());
        Collections.sort(wordFreqs, countComparator);
        return wordFreqs;
    }
    
    public static void storeCountList(List<Entry<BWord,Integer>> countList, String countType, String outFile) throws IOException
    {
        CSVPrinter printer = new CSVPrinter(new BufferedWriter(new FileWriter(outFile)),
                                    CSVFormat.DEFAULT.withHeader("frequency", countType));
        countList.forEach(freq -> {
            try {
                printer.printRecord(freq.getValue(),freq.getKey().toString());
            } catch (IOException ex) {
                Logger.getLogger(SVOStats.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        printer.close();
    }
    
    public static ArrayList<SVO> loadAllSVOsFromCSV(String fileName) throws FileNotFoundException, IOException
    {
        ArrayList<SVO> svos = new ArrayList<>();
        
        Reader csvData = new FileReader(fileName);
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(csvData);
        
        for(CSVRecord rec : records)
        {
            svos.add(new SVO(   rec.get("subject"),
                                Boolean.parseBoolean(rec.get("subjectNegated")),
                                rec.get("verb"),
                                Boolean.parseBoolean(rec.get("verbNegated")),
                                rec.get("object"),
                                Boolean.parseBoolean(rec.get("objectNegated")),
                                Double.parseDouble(rec.get("sentimentClass"))));
        }     
        return svos;
    }
}
