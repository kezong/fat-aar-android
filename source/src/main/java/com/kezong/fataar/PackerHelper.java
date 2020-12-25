package com.kezong.fataar;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * wangpengcheng.wpc create at 2020/12/21
 * <p>
 * Dealing with aar issues, Guarantee successful packaging
 */
public class PackerHelper {

    private static HashMap<String, String> namespaces = new HashMap();
    private static String ELE_ATTR = "attr";
    private static String ELE_DECLARE_STYLEABLE = "declare-styleable";
    private static Logger fLogger = null;


    public static void init(Logger logger) {
        fLogger = logger;
        namespaces.put("android", "http://schemas.android.com/apk/res/android");
    }

    public static void excludeDeclareStyleAttr(File file, List<String> attrs) {

        if (!file.exists()) {
            return;
        }

        logInfo("start exclude attr : " + file.getAbsolutePath());

        try {

            SAXReader xmlReader = new SAXReader();

            Document doc = xmlReader.read(file);

            final Element rootEle = doc.getRootElement();

            if (rootEle == null) {
                return;
            }

            List<Element> styleableEles = rootEle.elements("declare-styleable");

            for (Element declareStyle : styleableEles) {
                if (attrs.contains(getName(declareStyle))) {
                    logInfo("remove declare-styleable :" + getName(declareStyle));
                    declareStyle.getParent().remove(declareStyle);
                }
            }

            XMLWriter writer = new XMLWriter(new FileOutputStream(file));
            writer.write(doc);
            writer.close();

        } catch (Exception e) {
            logInfo("exclude attr failed : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void excludeApplicationAttr(AndroidArchiveLibrary archiveLibrary, List<String> deletedAttrs) {


        try {

            File file = archiveLibrary.getManifest();

            if (file == null || !file.exists()) {
                return;
            }

            if (deletedAttrs == null) {
                return;
            }


            SAXReader xmlReader = new SAXReader();

            Document doc = xmlReader.read(file);

            final Element rootEle = doc.getRootElement();

            if (rootEle == null) {
                return;
            }

            final Element application = rootEle.element("application");

            if (application == null) {
                return;
            }

            for (String attrDel : deletedAttrs) {
                String[] qNames = attrDel.split(":");
                String nameSpacesUrl = "";
                if (qNames.length > 1) {
                    nameSpacesUrl = namespaces.get(qNames[0]);
                }
                Attribute deletedAttr = null;
                if (nameSpacesUrl != null && nameSpacesUrl.length() > 0) {
                    Namespace namespace = new Namespace(qNames[0], nameSpacesUrl);
                    deletedAttr = application.attribute(QName.get(qNames[1], namespace));
                } else {
                    deletedAttr = application.attribute(attrDel);
                }

                if (deletedAttr != null) {
                    application.remove(deletedAttr);
                    logInfo("delete application attr :" + archiveLibrary.getMavenCoord() + "->" + deletedAttr.getQualifiedName());
                }
            }

            XMLWriter writer = new XMLWriter(new FileOutputStream(file));
            writer.write(doc);
            writer.close();

        } catch (Exception e) {
            logInfo("delete application attr failed : " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static void abiFilter(AndroidArchiveLibrary archiveLibrary, List<String> supportAbi) {

        if (supportAbi != null && supportAbi.isEmpty()) {
            return;
        }

        File soDir = archiveLibrary.getJniFolder();

        File[] abiDir = soDir.listFiles();

        if (abiDir != null && abiDir.length > 0) {
            logInfo("abi filter : " + archiveLibrary.getMavenCoord());
            for (File abi : abiDir) {
                if (!supportAbi.contains(abi.getName())) {
                    removeDir(abi);
                    abi.delete();
                }
            }
        }
    }

    public static void excludeSo(File soDir, List<String> soList) {

        File[] abiDir = soDir.listFiles();

        if (abiDir != null && abiDir.length > 0) {
            for (File abi : abiDir) {
                if (abi.isDirectory() && abi.listFiles().length > 0) {
                    for (File soFile : abi.listFiles()) {
                        if (soList.contains(soFile.getName())) {
                            logInfo("delete so :" + soFile.getAbsolutePath());
                            soFile.delete();
                        }
                    }
                }
            }
        }
    }

    private static void removeDir(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] files = fileOrDirectory.listFiles();
            if (files == null || files.length == 0) {
                return;
            }
            for (File child : files) {
                if (child.isDirectory()) {
                    removeDir(child);
                } else {
                    child.delete();
                }
            }
        }
    }

    public static void splitValuesXmlRepeatAttr(File pkgFile) {
        logInfo("handleResFile : " + pkgFile.getAbsolutePath());
        final File valueXml = new File(pkgFile, "res/values/values.xml");
        if (!valueXml.exists()) {
            logInfo("un exist " + valueXml.getAbsolutePath());
            return;
        }
        try {
            splitRepeatAttr(new File(pkgFile, "res/values/"), valueXml);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /***
     *split repeat attr to diff values.xml
     */
    private static void splitRepeatAttr(File valueXmlDir, final File file) {

        try {
            logInfo("deleteDuplicateAttr : " + file.getAbsolutePath());

            HashMap<String, ArrayList<Element>> needMovedDeclareStyles = new HashMap<String, ArrayList<Element>>();

            SAXReader xmlReader = new SAXReader();

            Document doc = xmlReader.read(file);

            final Element rootEle = doc.getRootElement();

            if (rootEle == null) {
                return;
            }

            List<Element> styleableEles = rootEle.elements(ELE_DECLARE_STYLEABLE);

            final HashSet<StyleAttr> repeatStyleAttr = new HashSet<StyleAttr>();
            final HashSet<StyleAttr> styleAttrs = new HashSet<StyleAttr>();

            for (Element declareStyle : styleableEles) {
                List<Element> attrs = declareStyle.elements(ELE_ATTR);
                for (Element attr : attrs) {
                    StyleAttr styleAttr = getStyleAttr(attr);
                    if (styleAttr != null) {
                        if (styleAttrs.contains(styleAttr)) {
                            repeatStyleAttr.add(styleAttr);
                        } else {
                            styleAttrs.add(styleAttr);
                        }
                    }
                }
            }

            for (Element styleEle : styleableEles) {
                List<Element> attrs = styleEle.elements(ELE_ATTR);
                for (Element attr : attrs) {
                    StyleAttr repeatAttr = getStyleAttr(attr);
                    if (repeatAttr != null) {
                        if (repeatStyleAttr.contains(repeatAttr)) {

                            if (needMovedDeclareStyles.get(repeatAttr.name) == null) {
                                logInfo("split attr : " + repeatAttr.name);
                                needMovedDeclareStyles.put(repeatAttr.name, new ArrayList());
                            }

                            if (!hasContainElement(needMovedDeclareStyles, styleEle)) {
                                needMovedDeclareStyles.get(repeatAttr.name).add(styleEle);
                            }
                        }
                    }

                }
            }

            List<Element> maxElementList = new ArrayList();

            for (ArrayList<Element> elements : needMovedDeclareStyles.values()) {
                if (elements.size() > maxElementList.size()) {
                    maxElementList = elements;
                }

                for (Element ele : elements) {
                    ele.getParent().remove(ele);
                    ele.detach();
                }
            }

            XMLWriter writer = new XMLWriter(new FileOutputStream(file));
            writer.write(doc);
            writer.close();

            for (int index = 0; index < maxElementList.size(); index++) {

                File newValuesFile = new File(valueXmlDir, "values" + index + ".xml");

                Document newDoc = DocumentHelper.createDocument();
                Element newRoot = newDoc.addElement("resources");

                for (ArrayList<Element> elements : needMovedDeclareStyles.values()) {
                    if (elements.size() > index) {
                        newRoot.add((Element) elements.get(index).clone());
                    }
                }

                logInfo("write split attr to file : " + newValuesFile.getAbsolutePath());

                XMLWriter newWriter = new XMLWriter(new FileOutputStream(newValuesFile));
                newWriter.write(newDoc);
                newWriter.close();
            }

        } catch (Exception e) {
            logInfo("重复 attr 剔除失败 : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static StyleAttr getStyleAttr(Element element) {
        Attribute name = element.attribute(StyleAttr.ATTR_NAME);
        Attribute format = element.attribute(StyleAttr.ATTR_FORMAT);
        if (name != null && format != null) {
            return new StyleAttr(name.getValue(), format.getValue());
        }
        return null;
    }

    private static Boolean hasContainElement(HashMap<String, ArrayList<Element>> eleMap, Element ele) {

        if (eleMap == null) return false;

        String eleName = getName(ele);

        for (List<Element> elements : eleMap.values()) {

            for (Element e : elements) {
                if (getName(e).equals(eleName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String getName(Element ele) {
        return ele.attribute(StyleAttr.ATTR_NAME).getValue();
    }

    public static void logInfo(String msg) {
        if (fLogger != null) {
            fLogger.info(msg);
        }
    }


}
