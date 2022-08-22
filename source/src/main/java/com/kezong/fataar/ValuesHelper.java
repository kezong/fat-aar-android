package com.kezong.fataar;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.gradle.api.Project;


public class ValuesHelper {
    private static Project mProject;
    private static final String ELE_ATTR = "attr";
    private static final String ELE_DECLARE_STYLEABLE = "declare-styleable";

    public static void attach(Project project) {
        // fix: org.xml.sax.SAXNotRecognizedException: unrecognized feature http://xml.org/sax/features/external-general-entities
        System.setProperty("javax.xml.parsers.SAXParserFactory",
                "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
        mProject = project;
        project.getLogger().info("attach Values Helper");
    }

    public static void splitValuesXmlRepeatAttr(File pkgFile) {

        final File valueXml = new File(pkgFile, "res/values/values.xml");
        if (!valueXml.exists()) {
            mProject.getLogger().info("ValuesHelper: value xml doesn't exist...");
            return;
        }
        try {
            formatRepeatAttr(valueXml);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void formatRepeatAttr(final File file) {
        try {
            mProject.getLogger()
                    .info("ValuesHelper: deleteDuplicateAttr : " + file.getAbsolutePath());

            SAXReader xmlReader = new SAXReader();

            Document doc = xmlReader.read(file);

            final Element rootEle = doc.getRootElement();

            if (rootEle == null) {
                return;
            }
            List<Element> rootAttr = rootEle.elements(ELE_ATTR);
            List<Element> styleableElements = rootEle.elements(ELE_DECLARE_STYLEABLE);

            final Set<StyleAttr> repeatStyleAttr = new HashSet<>();
            final Set<StyleAttr> styleAttrs = new HashSet<>();

            //先查找根节点<attr
            for (Element attr : rootAttr) {
                StyleAttr styleAttr = getStyleAttr(attr);
                if (styleAttr != null) {
                    if (styleAttrs.contains(styleAttr)) {
                        repeatStyleAttr.add(styleAttr);
                    } else {
                        styleAttrs.add(styleAttr);
                    }
                    if (repeatStyleAttr.contains(styleAttr)) {
                        Attribute format = attr.attribute(StyleAttr.ATTR_FORMAT);
                        attr.remove(format);
                    }
                }
            }

            // 只保留第一个attr的定义
            for (Element declareStyle : styleableElements) {
                List<Element> attrs = declareStyle.elements(ELE_ATTR);
                for (Element attr : attrs) {
                    StyleAttr styleAttr = getStyleAttr(attr);
                    if (styleAttr != null) {
                        if (styleAttrs.contains(styleAttr)) {
                            repeatStyleAttr.add(styleAttr);
                        } else {
                            styleAttrs.add(styleAttr);
                        }
                        if (repeatStyleAttr.contains(styleAttr)) {
                            Attribute format = attr.attribute(StyleAttr.ATTR_FORMAT);
                            attr.remove(format);
                        }
                    }
                }
            }


            XMLWriter writer = new XMLWriter(new FileOutputStream(file));
            writer.write(doc);
            writer.close();

        } catch (Exception e) {
            mProject.getLogger()
                    .info("ValuesHelper: exclude repeat attr failed : " + e.getMessage());
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
}
