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

    ArrayList root = new JsonSlurper().parse(is).Records

    def outData

    // empty input
    if (!root) {
      outData = []
    }

    // UPDATE response from Db only contains Ids
    else if (root.ColumnKey.unique() == [null]) {
      outData = [
        HeaderRows: ["Col18": "PivotedDataConfigId"],
        PivotOnConfigs: root.PivotedDataConfigId.collect{ ["Col18": it]}
      ]
    }

    // READ response
    else {
        // --- calculate numKeys --- //

        ArrayList numKeysArr = []
        root.each { item ->
          numKeysArr << item.ColumnKey.split(DBIFS).size()
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
        typesRowArr = []
        rowArr = []

        Boolean firstItem = true
        root.each { item ->

          LinkedHashMap row = [:]
          LinkedHashMap typesRow = [:]

          def columnLabelsArr = item.ColumnLabels.split(DBIFS)
          for(int k = 0; k < numKeys; k++ ) {
            if (firstItem) {
              headerRow << ["Col$k": "Column Label ${k+1}"]
              typesRow << ["Col$k": "String"]
            }

            row << ["Col$k": columnLabelsArr[k]]
          }

          if (firstItem) {
            headerRow << ["Col${numKeys+0}": "Active"]
            headerRow << ["Col${numKeys+1}": "Sub-Table Index"]
            headerRow << ["Col${numKeys+2}": "Column Width"]
            headerRow << ["Col${numKeys+3}": "Suppress Column If No Data For All Rows"]
            headerRow << ["Col16": "ColumnKey"]
            headerRow << ["Col17": "ColumnLabels"]
            headerRow << ["Col18": "PivotedDataConfigId"]
            headerRow << ["Col19": "ColumnIndex"]
            typesRow << ["Col${numKeys+0}": true]
            typesRow << ["Col${numKeys+1}": "int"]
            typesRow << ["Col${numKeys+2}": "int"]
            typesRow << ["Col${numKeys+3}": true]
            typesRow << ["Col16": "String"]
            typesRow << ["Col17": "String"]
            typesRow << ["Col18": "int"]
            typesRow << ["Col19": "int"]

            rowArr << typesRow
          }

          row << ["Col${numKeys+0}": item.Active]
          row << ["Col${numKeys+1}": item.SubTableIndex]
          row << ["Col${numKeys+2}": item.ColumnWidth]
          row << ["Col${numKeys+3}": item.SuppressIfNoDataForAllRows]
          row << ["Col16": item.ColumnKey]
          row << ["Col17": item.ColumnLabels]
          row << ["Col18": item.PivotedDataConfigId]
          row << ["Col19": item.ColumnIndex]

          rowArr << row

          firstItem = false
        }

        def headerRowArr = [headerRow]

        // println headerRowArr
        // println rowArr



        // --- result --- //

        outData = [ 
          HeaderRows: headerRowArr,
          PivotOnConfigs: rowArr
        ]
        // println prettyJson(outData)
    }   


    is = new ByteArrayInputStream(prettyJson(outData).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { 
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
