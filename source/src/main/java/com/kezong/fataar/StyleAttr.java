package com.kezong.fataar;

//<attr/> at value.xml
public class StyleAttr {
    public StyleAttr(String name_, String format_) {
        name = name_;
        format = format_;
    }

    public static final String ATTR_NAME = "name";
    public static final String ATTR_FORMAT = "format";
    public String name = "";
    public String format = "";

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StyleAttr styleAttr = (StyleAttr) o;

        if (!name.equals(styleAttr.name)) return false;
        return format.equals(styleAttr.format);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + format.hashCode();
        return result;
    }

}
