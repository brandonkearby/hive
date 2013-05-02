package org.apache.hadoop.hive.ql.cube.parse;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.ql.cube.metadata.CubeFactTable;
import org.apache.hadoop.hive.ql.cube.metadata.UpdatePeriod;
import org.apache.hadoop.hive.ql.parse.SemanticException;

public class PartitionResolver implements ContextRewriter {

  public PartitionResolver(Configuration conf) {
  }

  @Override
  public void rewriteContext(CubeQueryContext cubeql) throws SemanticException {
    if (!cubeql.getCandidateFactTables().isEmpty()) {
      Map<CubeFactTable, Map<UpdatePeriod, List<String>>> factPartitionMap =
          new HashMap<CubeFactTable, Map<UpdatePeriod, List<String>>>();
      Date fromDate = cubeql.getFromDate();
      Date toDate = cubeql.getToDate();

      Calendar cal = Calendar.getInstance();
      cal.setTime(fromDate);
      for (Iterator<CubeFactTable> i = cubeql.getCandidateFactTables().iterator(); i.hasNext();) {
        CubeFactTable fact = i.next();
        Map<UpdatePeriod, List<String>> partitionColMap =
            new HashMap<UpdatePeriod, List<String>>();
        factPartitionMap.put(fact, partitionColMap);
        if (!getPartitions(fact, fromDate, toDate, partitionColMap, cubeql)) {
          i.remove();
        }
      }
      // set partition cols map in cubeql
      cubeql.setFactPartitionMap(factPartitionMap);
    }
  }

  private boolean getPartitions(CubeFactTable fact, Date fromDate, Date toDate,
      Map<UpdatePeriod, List<String>> partitionColMap, CubeQueryContext cubeql)
          throws SemanticException {
    if (fromDate.equals(toDate) || fromDate.after(toDate)) {
      return true;
    }

    UpdatePeriod interval = fact.maxIntervalInRange(fromDate, toDate);
    if (interval == null) {
      System.err.println("Could not find a partition for given range:"
          + fromDate + "-" + toDate + " in fact:" + fact.getName());
      return false;
    }

    Date ceilFromDate = DateUtil.getCeilDate(fromDate, interval);
    Date floorToDate = DateUtil.getFloorDate(toDate, interval);
    List<String> partitions = fact.getPartitions(ceilFromDate, floorToDate,
        interval);
    if (partitions != null) {
      List<String> parts = partitionColMap.get(interval);
      if (parts == null) {
        parts = new ArrayList<String>();
        partitionColMap.put(interval, parts);
      }
      parts.addAll(partitions);
    }
    return (getPartitions(fact, fromDate, ceilFromDate, partitionColMap, cubeql)
        && getPartitions(fact, floorToDate, toDate, partitionColMap, cubeql));
  }
}
