package org.verapdf.tools;

import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDFontDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Cli {
    private static final String HELP = "Arguments: inputFile";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println(HELP);
            return;
        }
        int counter = 0;
        Path path = Paths.get(args[0]);
        Path finalPath = Paths.get(System.getProperty("user.dir") + "\\fixed_files");
        if (!Files.exists(finalPath)) {
            Files.createDirectory(Paths.get(String.valueOf(finalPath)));
        }

        List<String> pathes = null;
        try (Stream<Path> subPaths = Files.walk(path)) {
            pathes = subPaths.filter(Files::isRegularFile)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        List<File> filesList = new ArrayList<>();
        assert pathes != null;
        for (String str : pathes) {
            if (str.endsWith(".pdf")) {
                filesList.add(new File(str));
            }
        }
        for (File file : filesList) {
            try (PDDocument pdDocument = PDDocument.load(file)) {
                if (pdDocument.isEncrypted()) {
                    try {
                        pdDocument.setAllSecurityToBeRemoved(true);
                    } catch (Exception e) {
                        throw new Exception("The document is encrypted, and we can't decrypt it.", e);
                    }
                }
                if (procSet(pdDocument) != null || CIDSet(pdDocument) != null || charSet(pdDocument) != null || name(pdDocument) != null) {
                    System.out.println(file.getPath());
                    if (procSet(pdDocument) != null) {
                        System.out.println("Procset is in these objects: " + procSet(pdDocument));
                        removeProcSet(pdDocument, procSet(pdDocument));
                    }
                    if (CIDSet(pdDocument) != null) {
                        System.out.println("CIDSet is in these objects: " + CIDSet(pdDocument));
                        removeCIDSet(pdDocument, CIDSet(pdDocument));
                    }
                    if (charSet(pdDocument) != null) {
                        System.out.println("CharSet is in these objects: " + charSet(pdDocument));
                        removeCharSet(pdDocument, charSet(pdDocument));
                    }
                    if (name(pdDocument) != null) {
                        System.out.println("Name is in these objects: " + name(pdDocument));
                        removeName(pdDocument, name(pdDocument));

                    }
                    counter++;
                    System.out.println("");
                    pdDocument.save(new File(String.valueOf(finalPath), "fix_" + file.getName()));

                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("There is " + counter + " files with deprecated features");
    }

    public static List<Long> procSet(PDDocument document) {
        List<Long> list = new ArrayList<>();
        COSDocument cosDocument = document.getDocument();
        for (COSObject object : cosDocument.getObjects()) {
            COSBase baseObject = object.getObject();
            if (baseObject instanceof COSStream) {
                COSStream stream = (COSStream) baseObject;
                if (stream.containsKey(COSName.PROC_SET)) {
                    if (!list.contains(stream.getKey().getNumber())) {
                        list.add(stream.getKey().getNumber());
                    }
                }
                COSDictionary dict = (COSDictionary) stream.getDictionaryObject(COSName.RESOURCES);
                if (dict != null) {
                    if (dict.containsKey(COSName.PROC_SET)) {
                        if (!list.contains(stream.getKey().getNumber())) {
                            list.add(stream.getKey().getNumber());
                        }
                    }
                }
            }
            if (baseObject instanceof COSDictionary) {
                COSDictionary dict = (COSDictionary) ((COSDictionary) baseObject).getDictionaryObject(COSName.RESOURCES);
                if (dict != null) {
                    if (dict.containsKey(COSName.PROC_SET)) {
                        if (!list.contains(baseObject.getKey().getNumber())) {
                            list.add(baseObject.getKey().getNumber());
                        }
                    }
                }
                if (((COSDictionary) baseObject).containsKey(COSName.PROC_SET)) {
                    if (!list.contains(baseObject.getKey().getNumber())) {
                        list.add(baseObject.getKey().getNumber());
                    }
                }
            }
        }
        if (list.size() > 0) {
            return Collections.unmodifiableList(list);
        }
        return null;
    }

    public static void removeCIDSet(PDDocument document, List<Long> list) throws IOException {
        for (PDPage page : document.getPages()) {
            PDResources resources = page.getResources();
            PDFont font;
            for (COSName fontName : resources.getFontNames()) {
                font = resources.getFont(fontName);
                PDFontDescriptor fontDescriptor = font.getFontDescriptor();
                fontDescriptor.getCOSObject().removeItem(COSName.CID_SET);
            }
        }
    }

    public static void removeCharSet(PDDocument document, List<Long> list) throws IOException {
        COSDocument cosDocument = document.getDocument();
        for (COSObject object : cosDocument.getObjects()) {
            if (list.contains(object.getObjectNumber())) {
                COSBase baseObject = object.getObject();
                if (baseObject instanceof COSStream) {
                    COSStream stream = (COSStream) baseObject;
                    stream.removeItem(COSName.CHAR_SET);
                    COSDictionary dict = (COSDictionary) stream.getDictionaryObject(COSName.RESOURCES);
                    if (dict != null) {
                        stream.removeItem(COSName.CHAR_SET);
                    }
                }
                if (baseObject instanceof COSDictionary) {
                    ((COSDictionary) baseObject).removeItem(COSName.CHAR_SET);
                    COSDictionary dict = (COSDictionary) ((COSDictionary) baseObject).getDictionaryObject(COSName.RESOURCES);
                    if (dict != null) {
                        dict.removeItem(COSName.CHAR_SET);
                    }
                }
            }
        }
    }

    public static void removeProcSet(PDDocument document, List<Long> list) {
        COSDocument cosDocument = document.getDocument();
        for (COSObject object : cosDocument.getObjects()) {
            if (list.contains(object.getObjectNumber())) {
                COSBase baseObject = object.getObject();
                if (baseObject instanceof COSStream) {
                    COSStream stream = (COSStream) baseObject;
                    stream.removeItem(COSName.PROC_SET);
                    COSDictionary dict = (COSDictionary) stream.getDictionaryObject(COSName.RESOURCES);
                    if (dict != null) {
                        stream.removeItem(COSName.PROC_SET);
                    }
                }
                if (baseObject instanceof COSDictionary) {
                    ((COSDictionary) baseObject).removeItem(COSName.PROC_SET);
                    COSDictionary dict = (COSDictionary) ((COSDictionary) baseObject).getDictionaryObject(COSName.RESOURCES);
                    if (dict != null) {
                        dict.removeItem(COSName.PROC_SET
                        );
                    }
                }
            }
        }
    }

    public static void removeName(PDDocument document, List<Long> list) {
        COSDocument cosDocument = document.getDocument();
        for (COSObject object : cosDocument.getObjects()) {
            if (list.contains(object.getObjectNumber())) {
                COSBase baseObject = object.getObject();
                if (baseObject instanceof COSStream) {
                    COSStream stream = (COSStream) baseObject;
                    stream.removeItem(COSName.NAME);
                    COSDictionary dict = (COSDictionary) stream.getDictionaryObject(COSName.RESOURCES);
                    if (dict != null) {
                        stream.removeItem(COSName.NAME);
                    }
                }
                if (baseObject instanceof COSDictionary) {
                    ((COSDictionary) baseObject).removeItem(COSName.NAME);
                    COSDictionary dict = (COSDictionary) ((COSDictionary) baseObject).getDictionaryObject(COSName.RESOURCES);
                    if (dict != null) {
                        dict.removeItem(COSName.NAME
                        );
                    }
                }
            }
        }
    }

    public static List<Long> CIDSet(PDDocument document) throws IOException {
        List<Long> list = new ArrayList<>();
        COSDocument cosDocument = document.getDocument();
        for (COSObject object : cosDocument.getObjects()) {
            COSBase baseObject = object.getObject();
            if (baseObject instanceof COSStream) {
                COSStream stream = (COSStream) baseObject;
                if (stream.containsKey(COSName.CID_SET)) {
                    if (!list.contains(stream.getKey().getNumber())) {
                        list.add(stream.getKey().getNumber());
                    }
                }
                COSDictionary cosDictionary = (COSDictionary) (stream.getDictionaryObject(COSName.RESOURCES));
                if (cosDictionary != null) {
                    if (cosDictionary.containsKey(COSName.CID_SET)) {
                        if (!list.contains(stream.getKey().getNumber())) {
                            list.add(stream.getKey().getNumber());
                        }
                    }
                }
            }

            if (baseObject instanceof COSDictionary) {
                COSDictionary dict = (COSDictionary) ((COSDictionary) baseObject).getDictionaryObject(COSName.RESOURCES);
                if (dict != null) {
                    if (dict.containsKey(COSName.CID_SET)) {
                        if (!list.contains(baseObject.getKey().getNumber())) {
                            list.add(baseObject.getKey().getNumber());
                        }
                    }
                }
                if (((COSDictionary) baseObject).containsKey(COSName.CID_SET)) {
                    if (!list.contains(baseObject.getKey().getNumber())) {
                        list.add(baseObject.getKey().getNumber());
                    }
                }
            }
        }
        if (!list.isEmpty()) {
            return Collections.unmodifiableList(list);
        }
        return null;
    }

    public static List<Long> charSet(PDDocument document) throws IOException {
        List<Long> list = new ArrayList<>();
        COSDocument cosDocument = document.getDocument();
        for (COSObject object : cosDocument.getObjects()) {
            COSBase baseObject = object.getObject();
            if (baseObject instanceof COSStream) {
                COSStream stream = (COSStream) baseObject;
                if (stream.containsKey(COSName.CHAR_SET)) {
                    if (!list.contains(stream.getKey().getNumber())) {
                        list.add(stream.getKey().getNumber());
                    }
                }
                COSDictionary cosDictionary = (COSDictionary) (stream.getDictionaryObject(COSName.RESOURCES));
                if (cosDictionary != null) {
                    if (cosDictionary.containsKey(COSName.CHAR_SET)) {
                        if (!list.contains(stream.getKey().getNumber())) {
                            list.add(stream.getKey().getNumber());
                        }
                    }
                }
            }
            if (baseObject instanceof COSDictionary) {
                COSDictionary dict = (COSDictionary) ((COSDictionary) baseObject).getDictionaryObject(COSName.RESOURCES);
                if (dict != null) {
                    if (dict.containsKey(COSName.CHAR_SET)) {
                        if (!list.contains(baseObject.getKey().getNumber())) {
                            list.add(baseObject.getKey().getNumber());
                        }
                    }
                }
                COSDictionary cosDictionary = (COSDictionary) baseObject;
                if (cosDictionary.containsKey(COSName.CHAR_SET)) {
                    if (!list.contains(baseObject.getKey().getNumber())) {
                        list.add(baseObject.getKey().getNumber());
                    }
                }
            }
        }
        if (!list.isEmpty()) {
            return Collections.unmodifiableList(list);
        }
        return null;
    }

    public static List<Long> name(PDDocument document) {
        List<Long> list = new ArrayList<>();
        COSDocument cosDocument = document.getDocument();
        for (COSObject object : cosDocument.getObjects()) {
            COSBase baseObject = object.getObject();
            if (baseObject instanceof COSStream) {
                COSStream stream = (COSStream) baseObject;
                if (stream.containsKey(COSName.NAME)) {
                    if (COSName.TYPE1.equals(stream.getDictionaryObject(COSName.SUBTYPE)) ||
                            (COSName.TYPE3).equals(stream.getDictionaryObject(COSName.SUBTYPE)) ||
                            (COSName.IMAGE).equals(stream.getDictionaryObject(COSName.SUBTYPE)) ||
                            (COSName.FORM).equals(stream.getDictionaryObject(COSName.SUBTYPE)) ||
                            (COSName.TRUE_TYPE).equals(stream.getDictionaryObject(COSName.SUBTYPE))) {

                        if (!list.contains(stream.getKey().getNumber())) {
                            list.add(stream.getKey().getNumber());
                        }
                    }
                }
                COSDictionary cosDictionary = (COSDictionary) (stream.getDictionaryObject(COSName.RESOURCES));
                if (cosDictionary != null) {
                    if (cosDictionary.containsKey(COSName.NAME)) {
                        if (COSName.TYPE1.equals(cosDictionary.getDictionaryObject(COSName.SUBTYPE)) ||
                                (COSName.TYPE3).equals(cosDictionary.getDictionaryObject(COSName.SUBTYPE)) ||
                                (COSName.IMAGE).equals(cosDictionary.getDictionaryObject(COSName.SUBTYPE)) ||
                                (COSName.FORM).equals(cosDictionary.getDictionaryObject(COSName.SUBTYPE)) ||
                                (COSName.TRUE_TYPE).equals(cosDictionary.getDictionaryObject(COSName.SUBTYPE))) {
                            if (!list.contains(stream.getKey().getNumber())) {
                                list.add(stream.getKey().getNumber());
                            }
                        }
                    }
                }
            }
            if (baseObject instanceof COSDictionary) {
                COSDictionary dict = (COSDictionary) ((COSDictionary) baseObject).getDictionaryObject(COSName.RESOURCES);
                if (dict != null) {
                    if (dict.containsKey(COSName.NAME)) {
                        if (COSName.TYPE1.equals(dict.getDictionaryObject(COSName.SUBTYPE)) ||
                                (COSName.TYPE3).equals(dict.getDictionaryObject(COSName.SUBTYPE)) ||
                                (COSName.IMAGE).equals(dict.getDictionaryObject(COSName.SUBTYPE)) ||
                                (COSName.FORM).equals(dict.getDictionaryObject(COSName.SUBTYPE)) ||
                                (COSName.TRUE_TYPE).equals(dict.getDictionaryObject(COSName.SUBTYPE))) {
                            if (!list.contains(baseObject.getKey().getNumber())) {
                                list.add(baseObject.getKey().getNumber());
                            }
                        }
                    }
                }
                dict = (COSDictionary) baseObject;
                if (dict.containsKey(COSName.NAME)) {
                    if (COSName.TYPE1.equals(dict.getDictionaryObject(COSName.SUBTYPE)) ||
                            (COSName.TYPE3).equals(dict.getDictionaryObject(COSName.SUBTYPE)) ||
                            (COSName.IMAGE).equals(dict.getDictionaryObject(COSName.SUBTYPE)) ||
                            (COSName.FORM).equals(dict.getDictionaryObject(COSName.SUBTYPE)) ||
                            (COSName.TRUE_TYPE).equals(dict.getDictionaryObject(COSName.SUBTYPE))) {
                        if (!list.contains(baseObject.getKey().getNumber())) {
                            list.add(baseObject.getKey().getNumber());
                        }
                    }
                }
            }
        }
        if (!list.isEmpty()) {
            return Collections.unmodifiableList(list);
        }
        return null;
    }
}


