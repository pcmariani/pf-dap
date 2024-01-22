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

    ArrayList root = new JsonSlurper().parse(is)



    // --- calculate numKeys --- //

    ArrayList numKeysArr = []
    root.each { item ->
      numKeysArr << item.ColumnKey.split("\\^\\^\\^").size()
    }
    // println numKeysArr

    int numKeys
    if (numKeysArr.unique().size() == 1) {
      numKeys = numKeysArr.unique()[0]
    } else {
      println "UH OH"
    }



    // --- main logic --- //

    LinkedHashMap headerRow = [:]
    rowArr = []

    Boolean firstItem = true
    root.each { item ->

      LinkedHashMap row = [:]

      def columnLabelsArr = item.ColumnLabels.split(DBIFS)
      for(int k = 0; k < numKeys; k++ ) {
        if (firstItem) {
          headerRow << ["col$k": "Column Label ${k+1}"]
          row << ["col$k": "String"]
        }
        else {
          row << ["col$k": columnLabelsArr[k]]
        }
      }

      if (firstItem) {
        headerRow << ["col${numKeys+0}": "Active"]
        headerRow << ["col${numKeys+1}": "Sub-Table Index"]
        headerRow << ["col${numKeys+2}": "Column Width"]
        headerRow << ["col${numKeys+3}": "Suppress Column If No Data For All Rows"]
        headerRow << ["col18": "Id"]
        headerRow << ["col19": "ColumnIndex"]
        row << ["col${numKeys+0}": true]
        row << ["col${numKeys+1}": "int"]
        row << ["col${numKeys+2}": "int"]
        row << ["col${numKeys+3}": true]
        row << ["col18": "int"]
        row << ["col19": "int"]
      }
      else {
        row << ["col${numKeys+0}": item.Active]
        row << ["col${numKeys+1}": item.SubTableIndex]
        row << ["col${numKeys+2}": item.ColumnWidth]
        row << ["col${numKeys+3}": item.SuppressIfNoDataForAllRows]
        row << ["col18": item.PivotedDataConfigId]
        row << ["col19": item.ColumnIndex]
      }

      rowArr << row

      firstItem = false
    }

    def headerRowArr = [headerRow]

    // println headerRowArr
    // println rowArr



    // --- result --- //

    def outData = [ 
      HeaderRows: headerRowArr,
      PivotOnConfigs: rowArr
    ]
    // println prettyJson(outData)
       
    is = new ByteArrayInputStream(prettyJson(outData).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { 
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
