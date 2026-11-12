package com.municipal.rbaccore.infrastructure.excel;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/** 复用原工程的 XLSX 流式读取器，按实际单元格读取，不依赖错误的 A1 声明范围。 */
public final class ExcelXmlReader {
  private ExcelXmlReader() {}

  /** 保留原文件名及老师原件带括号文件名的兼容查找。 */
  public static Path permissionFile(Path directory) {
    Path file = directory.resolve("市政公司应用软件功能权限清单.xlsx");
    return Files.exists(file) ? file : directory.resolve("市政公司应用软件功能权限清单(1).xlsx");
  }

  /** 兼容旧调用方，仅返回单元格内容。 */
  public static List<List<String>> read(Path file) throws Exception {
    return readRows(file).stream().map(WorkbookRow::cells).toList();
  }

  /** 返回真实 Excel 行号，便于导入错误定位和持久化来源映射。 */
  public static List<WorkbookRow> readRows(Path file) throws Exception {
    try (ZipFile zip = new ZipFile(file.toFile())) {
      List<String> strings = shared(zip);
      ZipEntry sheet = zip.getEntry("xl/worksheets/sheet2.xml");
      if (sheet == null) {
        sheet = zip.getEntry("xl/worksheets/sheet1.xml");
      }
      if (sheet == null) {
        throw new XMLStreamException("缺少工作表");
      }
      try (InputStream input = zip.getInputStream(sheet)) {
        XMLStreamReader reader = factory().createXMLStreamReader(input);
        try {
          return rows(reader, strings);
        } finally {
          reader.close();
        }
      }
    }
  }

  /** 同时支持共享字符串、inlineStr 及数值文本，保留稀疏列和前导零。 */
  private static List<WorkbookRow> rows(XMLStreamReader reader, List<String> strings)
      throws Exception {
    List<WorkbookRow> rows = new ArrayList<>();
    List<String> cells = null;
    String type = null;
    String reference = null;
    StringBuilder value = new StringBuilder();
    int rowNumber = 0;
    boolean inValue = false;
    while (reader.hasNext()) {
      int event = reader.next();
      String element = reader.hasName() ? reader.getLocalName() : "";
      if (event == XMLStreamConstants.START_ELEMENT) {
        switch (element) {
          case "row" -> {
            cells = new ArrayList<>();
            String rowReference = reader.getAttributeValue(null, "r");
            rowNumber = rowReference == null ? rowNumber + 1 : Integer.parseInt(rowReference);
          }
          case "c" -> {
            type = reader.getAttributeValue(null, "t");
            reference = reader.getAttributeValue(null, "r");
            value.setLength(0);
          }
          case "v" -> inValue = true;
          case "t" -> inValue = "inlineStr".equals(type);
          default -> {}
        }
      } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
          && inValue) {
        value.append(reader.getText());
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        switch (element) {
          case "v", "t" -> inValue = false;
          case "c" -> {
            if (cells == null) {
              throw new XMLStreamException("单元格缺少所属行");
            }
            int column = column(reference);
            while (cells.size() <= column) {
              cells.add("");
            }
            String text = value.toString();
            cells.set(
                column,
                "s".equals(type) && !text.isEmpty() ? strings.get(Integer.parseInt(text)) : text);
          }
          case "row" -> rows.add(new WorkbookRow(rowNumber, List.copyOf(cells)));
          default -> {}
        }
      }
    }
    return List.copyOf(rows);
  }

  /** 共享字符串可缺省；富文本仅拼接 t 节点，避免把 XML 缩进当作内容。 */
  private static List<String> shared(ZipFile zip) throws Exception {
    ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
    if (entry == null) {
      return List.of();
    }
    List<String> strings = new ArrayList<>();
    try (InputStream input = zip.getInputStream(entry)) {
      XMLStreamReader reader = factory().createXMLStreamReader(input);
      try {
        StringBuilder value = new StringBuilder();
        boolean inText = false;
        while (reader.hasNext()) {
          int event = reader.next();
          String element = reader.hasName() ? reader.getLocalName() : "";
          if (event == XMLStreamConstants.START_ELEMENT && element.equals("si")) {
            value.setLength(0);
          } else if (event == XMLStreamConstants.START_ELEMENT && element.equals("t")) {
            inText = true;
          } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
              && inText) {
            value.append(reader.getText());
          } else if (event == XMLStreamConstants.END_ELEMENT && element.equals("t")) {
            inText = false;
          } else if (event == XMLStreamConstants.END_ELEMENT && element.equals("si")) {
            strings.add(value.toString());
          }
        }
      } finally {
        reader.close();
      }
    }
    return strings;
  }

  /** 禁用 DTD 与外部实体，读取工作簿不访问外部资源。 */
  private static XMLInputFactory factory() {
    XMLInputFactory factory = XMLInputFactory.newFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
    return factory;
  }

  /** 把 A、AA 等列引用转换成零基下标。 */
  private static int column(String reference) throws XMLStreamException {
    int number = 0;
    if (reference != null) {
      for (char character : reference.toCharArray()) {
        if (character < 'A' || character > 'Z') {
          break;
        }
        number = number * 26 + character - 'A' + 1;
      }
    }
    if (number < 1 || number > 16384) {
      throw new XMLStreamException("无效单元格列引用");
    }
    return number - 1;
  }

  /** 工作簿中的一行，行号沿用源文件而不是压缩后的列表下标。 */
  public record WorkbookRow(int number, List<String> cells) {}
}
