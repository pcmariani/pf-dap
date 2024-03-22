import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;
import com.boomi.execution.ExecutionUtil;

String DBIFS = /\^\^\^/
String DBOFS = "^^^"
logger = ExecutionUtil.getBaseLogger()

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
    InputStream is = dataContext.getStream(i);
    Properties props = dataContext.getProperties(i);

    LinkedHashMap root = new JsonSlurper().parse(is)
    ArrayList configsArr = root.PivotOnConfigs[1..-1]

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

        def newLabelsArr = []
        (0..4).each { j ->
          if (item."Col${j}") {
            newLabelsArr << item."Col${j}".replaceAll(/\n/, "\\\\n")
          }
          else if (item."Col${j+5}") {
            newLabelsArr << item."Col${j+5}".replaceAll(/\n/, "\\\\n")
          }
          else {
            newLabelsArr << null
          }
        }
        // println newLabelsArr

        def labelsArr = item.Col17.split(DBIFS)
        // println labelsArr
        labelsArr.eachWithIndex { label, j ->
          def newLabel = newLabelsArr[j]
          if (newLabel) {
            labelsArr[j] = newLabel
          }
        }
        // println labelsArr

        rowArr << [
          PivotedDataConfigId: item."Col18",
          ColumnKey: item."Col16",
          ColumnLabels: labelsArr.join(DBOFS),
          Active: item."Col10",
          SuppressIfNoDataForAllRows: item."Col11",
          ColumnIndex: item."Col19",
          SubTableIndex: item."Col12",
          ColumnWidth: item."Col13",
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
