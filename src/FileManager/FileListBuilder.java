package FileManager;

import org.apache.commons.io.FilenameUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by robertrambo on 02/05/2017.
 */
public class FileListBuilder {
    String filename;
    String extension;
    String directory;
    String outputDirectory;
    File[] foundFiles;
    int indexOfDroppedFile;
    String base;
    /** File type ZIP archive */
    public static final int ZIPFILE = 0x504b0304;
    /** File type GZIP compressed Data */
    public static final int GZFILE = 0x1f8b0000;

    /**
     * Build a list of files from directory based on a single file
     * Used for loading SEC files that incremenet either leading or lagging in base name
     *
     * @param file single file that is representative of the list
     * @param dir directory location to look in
     * @throws Exception
     */
    public FileListBuilder(File file, String dir) throws Exception {
        // get directory
        // split
        extension = FilenameUtils.getExtension(file.getName());
        outputDirectory = dir;

        if (isZipFile(file)) {

            if (readZipFile(file)){
                //sort files
            } else {
                throw new Exception("No DAT Files in ZIP Archive");
            }

        } else if (isGZipFile(file)){

            throw new Exception("CANNOT PROCESS GZIP FILES, maybe later...");

        } else {

            indexOfDroppedFile = this.getFileIndex(file);
            filename = FilenameUtils.removeExtension(file.getName());
            String[] parts = filename.split("[-_]+");

//            for(int i=0; i<parts.length; i++){
//                System.out.println(i + " " + parts[i]);
//            }

            // ####_pbs_2.dat


            directory = file.getParent();
            /*
             * build a list of files that match
             *
             * 00001_filename.dat  <= that would be stupd
             * filename_00001.dat <= makes more sense
             */

            // make sure last part matches a number
            String onlyDigits = "\\d+";

            // match a digit and its length is greater than 1
            // 2_filename_000001.dat
            if (parts[parts.length-1].matches(onlyDigits) && parts[0].matches(onlyDigits)){ // if first and last match digits
                // decide if the front increments or last?
                if (parts[parts.length-1].length() > parts[0].length()){ // assume last is the digit to follow
                    this.collectFilesWithDigitsLast(parts[parts.length - 1]);
                } else { // 000001_filename_2.dat
                    this.collectFilesWithDigitsFirst(parts[0]);
                }

            } else if ( parts[parts.length-1].matches(onlyDigits)) { // if only last matches digits

                this.collectFilesWithDigitsLast(parts[parts.length - 1]);

            } else if (parts[0].matches(onlyDigits) ) { // 00001_filename.dat if only last matches digits

                this.collectFilesWithDigitsFirst(parts[0]);

            } else {
                throw new Exception();
            }


            //sanitize base
            this.sanitizeBase();

            int total = foundFiles.length;
            int locale=0;
            for(int i=0; i<total; i++){

                if (filename.equals(FilenameUtils.removeExtension(foundFiles[i].getName()))){
                    locale = i;
                    break;
                }
            }


            // check the time stamp before and after,
            double delta = 0;
            if (locale > 1){ // either zero or one
                //delta = 0.5*(foundFiles[locale+1].lastModified() - foundFiles[locale-1].lastModified()) ;
                double deltafirst, deltasecond;
                if (locale == (total-1)){ // locale is the last one
                    deltafirst = this.getFileIndex(foundFiles[locale]) - this.getFileIndex(foundFiles[locale-1]);
                    deltasecond = this.getFileIndex(foundFiles[locale-1]) - this.getFileIndex(foundFiles[locale-2]);
                } else {
                    deltafirst = this.getFileIndex(foundFiles[locale+1]) - indexOfDroppedFile;
                    deltasecond = indexOfDroppedFile - this.getFileIndex(foundFiles[locale-1]);

                }

                if (deltafirst/(deltafirst+deltasecond) < 0.58){ // nearly half
                    delta = 0.5*(deltafirst+deltasecond); // average the delta
                } else {
                    if (deltafirst < deltasecond){ // take smaller of the two
                        delta = deltafirst;
                    } else {
                        delta = deltasecond;
                    }
                }

            } else {
                delta = 0.5*(this.getFileIndex(foundFiles[2]) - this.getFileIndex(foundFiles[0]));
            }
            // purge files that are greater than 20% different from the time stamp?

            int startAt=0;
            for(int i=locale; i>1; i--){ // work backwards find first file that falls outside of delta

                if (( this.getFileIndex(foundFiles[i]) - this.getFileIndex(foundFiles[i-1])) > delta){
                    startAt = i;
                    break;
                }
            }



            int endAt = foundFiles.length;
            for(int i=locale+1; i<foundFiles.length; i++){
                if (( this.getFileIndex(foundFiles[i]) - this.getFileIndex(foundFiles[i-1])) > delta){
                    endAt = i;
                    break;
                }
            }


            //
            if ((endAt-startAt) < endAt){
                foundFiles = Arrays.copyOfRange(foundFiles, startAt, endAt);
            }

        }

    }

    public File[] getFoundFiles(){ return foundFiles; }

    private void collectFilesWithDigitsLast(String digits){

        String prefix = filename.split(digits)[0];
        base = prefix;
        // grab all files with the prefix
        File lookInThisDir = new File(directory);

        foundFiles = lookInThisDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith(prefix) && name.endsWith(".dat");
            }
        });


        // sort on custom Comparator
        Arrays.sort(foundFiles, new Comparator<File>() {

            @Override
            public int compare(File o1, File o2) {
                int n1 = extractNumber(o1.getName());
                int n2 = extractNumber(o2.getName());
                return n1 - n2;
            }

            private int extractNumber(String name) {
                int i = 0;

                try {
                    String tempName = FilenameUtils.removeExtension(name);
                    //int s = name.indexOf('_')+1;
                    //int e = name.lastIndexOf('.');
                    //String number = name.substring(s, e);
                    String number = tempName.substring(prefix.length(), tempName.length());

                    i = Integer.parseInt(number);
                } catch(Exception e) {
                    i = 0; // if filename does not match the format
                    // then default to 0
                }
                return i;
            }
        });

    }



    private void collectFilesWithDigitsFirst(String digits){

        String postfix = filename.split(digits)[1];

        base = postfix;

        // grab all files with the postfix
        File lookInThisDir = new File(directory);

        foundFiles = lookInThisDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(postfix+".dat");
            }
        });

        // sort on custom Comparator
        Arrays.sort(foundFiles, new Comparator<File>() {

            @Override
            public int compare(File o1, File o2) {
                int n1 = extractNumber(o1.getName());
                int n2 = extractNumber(o2.getName());
                return n1 - n2;
            }

            private int extractNumber(String name) {
                int i = 0;

                try {
                    String tempName = FilenameUtils.removeExtension(name);
                    //String number = tempName.split(postfix)[0]; // split on common part
                    String number = tempName.substring(0, tempName.indexOf(postfix));
                    i = Integer.parseInt(number);
                } catch(Exception e) {
                    i = 0; // if filename does not match the format
                    // then default to 0
                }
                return i;
            }
        });
    }

    private int getFileIndex(File file){
        int index = 0;

        String tempfilename = FilenameUtils.removeExtension(file.getName());
        String[] parts = tempfilename.split("[-_]+");

        // make sure last part matches a number
        String onlyDigits = "\\d+";

        if ( parts[parts.length-1].matches(onlyDigits) ) { // digits at end
            index = Integer.parseInt(parts[parts.length-1]);
        } else if (parts[0].matches(onlyDigits)) { // 00001_filename.dat
            index = Integer.parseInt(parts[0]);
        }
        return index;
    }


    /**
     * Determine whether a file is a ZIP File.
     */
    private boolean isZipFile(File file) throws IOException {
        if(file.isDirectory()) {
            return false;
        }
        if(!file.canRead()) {
            throw new IOException("Cannot read file "+file.getAbsolutePath());
        }
        if(file.length() < 4) {
            return false;
        }
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        int test = in.readInt();
        in.close();
        return test == ZIPFILE;
    }

    /**
     * Determine whether a file is a GZIP File.
     */
    private boolean isGZipFile(File file) throws IOException {
        if(file.isDirectory()) {
            return false;
        }
        if(!file.canRead()) {
            throw new IOException("Cannot read file "+file.getAbsolutePath());
        }
        if(file.length() < 4) {
            return false;
        }
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        int test = in.readInt();
        in.close();
        return test == GZFILE;
    }

    private boolean readZipFile(File file){

        byte[] buf = new byte[1024];
        ZipInputStream zipinputstream = null;
        ZipEntry zipentry;
        ArrayList<File> tempFiles = new ArrayList<>();
        int fileCount=0;

        try {
            zipinputstream = new ZipInputStream(new FileInputStream(file));
            zipentry = zipinputstream.getNextEntry();
            while (zipentry != null) {
                String entryName = zipentry.getName();
                FileOutputStream fileoutputstream;
                File newFile = new File(entryName);
                String directory = newFile.getParent();

                if (directory == null) {
                    if (newFile.isDirectory())
                        break;
                }

                if (entryName.endsWith(".dat")){
                    tempFiles.add(new File(outputDirectory+"\\" + entryName) );
                    fileoutputstream = new FileOutputStream(tempFiles.get(fileCount));

                    int n;
                    while ((n = zipinputstream.read(buf, 0, 1024)) > -1){
                        fileoutputstream.write(buf, 0, n);
                    }
                    fileoutputstream.close();
                }

                zipinputstream.closeEntry();
                zipentry = zipinputstream.getNextEntry();
            }
            zipinputstream.close();

            if (tempFiles.size() > 1){
                foundFiles = (File[])tempFiles.toArray();
                return true;
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    private void sanitizeBase(){

        // if first or last element of base does not match, trim
        int ll = base.length();

        if (!base.substring(0,1).matches("[0-9A-Za-z]") && base.substring(ll-1,ll).matches("[0-9A-Za-z]")){ // trim beginning

            while (!base.substring(0,1).matches("[0-9A-Za-z]")){
                base = base.substring(1);
            }

        } else if (base.substring(0,1).matches("[0-9A-Za-z]") && !base.substring(ll-1,ll).matches("[0-9A-Za-z]")){ //trim end

            while(!base.substring(ll-1,ll).matches("[0-9A-Za-z]")){
                base = base.substring(0, ll-1);
                ll -=1;
            }

        } else if (base.substring(0,1).matches("[0-9A-Za-z]") && base.substring(ll-1,ll).matches("[0-9A-Za-z]")){

            while (!base.substring(0,1).matches("[0-9A-Za-z]")){
                base = base.substring(1);
            }

            ll = base.length();
            while(!base.substring(ll-1,ll).matches("[0-9A-Za-z]")){
                base = base.substring(0, ll-1);
                ll -=1;
            }
        }

    }

    public String getBase(){
        return base;
    }

}

