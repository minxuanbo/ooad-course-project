package com.municipal.rbac;

import java.io.*; import java.nio.file.*; import java.util.*; import java.util.zip.*;
import javax.xml.stream.*;

final class ExcelXmlReader {
  private ExcelXmlReader() {}
  static Path permissionFile(Path directory) {
    Path file=directory.resolve("市政公司应用软件功能权限清单.xlsx");
    if(Files.exists(file))return file;
    return directory.resolve("市政公司应用软件功能权限清单(1).xlsx");
  }
  static List<List<String>> read(Path file) throws Exception {
    try (ZipFile zip=new ZipFile(file.toFile())) {
      List<String> strings=shared(zip); ZipEntry sheet=zip.getEntry("xl/worksheets/sheet2.xml");
      if(sheet==null) sheet=zip.getEntry("xl/worksheets/sheet1.xml");
      List<List<String>> rows=new ArrayList<>(); XMLStreamReader x=factory().createXMLStreamReader(zip.getInputStream(sheet));
      List<String> row=null; String type=null,ref=null,value=null; boolean inV=false;
      while(x.hasNext()) { int e=x.next();
        if(e==XMLStreamConstants.START_ELEMENT) switch(x.getLocalName()) {
          case "row" -> row=new ArrayList<>(); case "c" -> {type=x.getAttributeValue(null,"t");ref=x.getAttributeValue(null,"r");value="";} case "v" -> inV=true;
        } else if(e==XMLStreamConstants.CHARACTERS && inV) value+=x.getText();
        else if(e==XMLStreamConstants.END_ELEMENT) switch(x.getLocalName()) {
          case "v" -> inV=false; case "c" -> {int col=column(ref);while(row.size()<=col)row.add("");row.set(col,"s".equals(type)&&!value.isEmpty()?strings.get(Integer.parseInt(value)):value);} case "row" -> rows.add(row);
        }
      } x.close(); return rows;
    }
  }
  private static List<String> shared(ZipFile zip)throws Exception { List<String> out=new ArrayList<>(); XMLStreamReader x=factory().createXMLStreamReader(zip.getInputStream(zip.getEntry("xl/sharedStrings.xml"))); boolean in=false; StringBuilder s=null; while(x.hasNext()){int e=x.next();if(e==1&&x.getLocalName().equals("si")){in=true;s=new StringBuilder();}else if(e==4&&in)s.append(x.getText());else if(e==2&&x.getLocalName().equals("si")){out.add(s.toString());in=false;}}x.close();return out; }
  private static XMLInputFactory factory(){XMLInputFactory f=XMLInputFactory.newFactory();f.setProperty(XMLInputFactory.SUPPORT_DTD,false);f.setProperty("javax.xml.stream.isSupportingExternalEntities",false);return f;}
  private static int column(String ref){int n=0;for(char c:ref.toCharArray()){if(!Character.isLetter(c))break;n=n*26+c-'A'+1;}return n-1;}
}
