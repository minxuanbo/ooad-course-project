package com.municipal.rbac;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
interface DtoMapper {
  ObjectMapper MAPPER=new ObjectMapper();
  static <T> T one(Object value,Class<T> type){return MAPPER.convertValue(value,type);}
  static <T> List<T> list(List<? extends Map<String,?>> rows,Class<T> type){return rows.stream().map(row->one(row,type)).toList();}
}
