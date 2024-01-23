import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

String DBIFS = /\^\^\^/
logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    LinkedHashMap root = new JsonSlurper().parse(is)
    ArrayList configsArr = root.PivotOnConfigs

    // println configsArr

    if (!configsArr) {
      is = new ByteArrayInputStream(prettyJson(root).getBytes("UTF-8"));
    } 

    else {


      // --- calculate numKeys --- //

      ArrayList numKeysArr = []
      configsArr.each { item ->
        numKeysArr << item.Col16.split(DBIFS).size()
      }
      // println numKeysArr

      int numKeys
      if (numKeysArr.unique().size() == 1) {
        numKeys = numKeysArr.unique()[0]
      } else {
        println "UH OH"
      }



      // --- main logic --- //

      rowArr = []

      configsArr.each { item ->
        rowArr << [
          GroupByRowsConfigId: item."Col18",
          RowKey: item."Col16",
          RowLabels: item."Col17",
          Active: item."Col${numKeys+0}",
          SuppressIfNoDataForAllCols: item."Col${numKeys+3}",
          RowIndex: item."Col19",
        ]
      }
      // println prettyJson(rowArr)


      // --- result --- //

      def result = [
        Action: root.Action,
        Requestor: root.Requestor,
        ReportContentItem_DynamicTableId: root.ReportContentItem_DynamicTableId,
        Records: rowArr
      ]
      // println result

      is = new ByteArrayInputStream(prettyJson(result).getBytes("UTF-8"));
    }


    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { 
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
