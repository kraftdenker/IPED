package iped.engine.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class FilePathFilter {

    private List<Pattern> regexList;

    public FilePathFilter(List<String> regexList) {
        this.regexList = new ArrayList<>();
        for (String regex : regexList) {
            this.regexList.add(Pattern.compile(regex));
        }
    }
    
    public FilePathFilter(String regexList) {
        this.regexList = new ArrayList<>();
        for (String regex : regexList.split(";")) {
            this.regexList.add(Pattern.compile(regex));
        }
    }

    public boolean allow(File filepath) {
        for (Pattern regex : regexList) {
            if (regex.matcher(filepath.getAbsolutePath()).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean allow(String strFilepath) {
        for (Pattern regex : regexList) {
            if (regex.matcher(strFilepath).matches()) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        FilePathFilter filtro = new FilePathFilter(List.of("(.*)whatsapp(.*)\\.opus", ".*\\.pdf"));

        File arquivo1 = new File("c:\\whatsapp\\arquivo.opus");
        File arquivo2 = new File("arquivo.pdf");
        File arquivo3 = new File("arquivo.exe");

        System.out.println(filtro.allow(arquivo1)); // true
        System.out.println(filtro.allow(arquivo2)); // true
        System.out.println(filtro.allow(arquivo3)); // false

        FilePathFilter filtroT = new FilePathFilter("(.*)whatsapp(.*)\\.opus;");

        System.out.println(filtroT.allow(arquivo1)); // true
        System.out.println(filtroT.allow(arquivo2)); // true
        System.out.println(filtroT.allow(arquivo3)); // false
    }
    
   
}

