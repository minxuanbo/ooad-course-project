package com.municipal.rbaccore.api.role.support;

/** 角色接口统一清理首尾空白，兼容已有输入及原型中的Unicode分隔符。 */
public final class RoleText {
  private RoleText() {}

  /** 保留缺省与内部字符，仅清理文本边界。 */
  public static String strip(String text) {
    if (text == null) return null;
    int start = 0;
    int end = text.length();
    while (start < end && isBoundarySpace(text.charAt(start))) start++;
    while (end > start && isBoundarySpace(text.charAt(end - 1))) end--;
    return text.substring(start, end);
  }

  /** 保留原有ASCII清理范围，并识别全角、不换行空白和BOM。 */
  private static boolean isBoundarySpace(char character) {
    return character <= ' '
        || Character.isWhitespace(character)
        || Character.isSpaceChar(character)
        || character == '\uFEFF';
  }
}
