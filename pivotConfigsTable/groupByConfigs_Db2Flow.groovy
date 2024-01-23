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
    else if (root.RowKey.unique() == [null]) {
      outData = [
        HeaderColumns: [["Col18": "GroupByRowsConfigId"]],
        GroupByRowsConfigs: root.GroupByRowsConfigId.collect{ ["Col18": it]}
      ]
    }

    // READ response
    else {
        // --- calculate numKeys --- //

        ArrayList numKeysArr = []
        root.each { item ->
          numKeysArr << item.RowKey.split(DBIFS).size()
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

          def columnLabelsArr = item.RowLabels.split(DBIFS)
          for(int k = 0; k < numKeys; k++ ) {
            if (firstItem) {
              headerRow << ["Col$k": "Row Label ${k+1}"]
              typesRow << ["Col$k": "String"]
            }

            row << ["Col$k": columnLabelsArr[k]]
          }

          if (firstItem) {
            headerRow << ["Col${numKeys+0}": "Active"]
            headerRow << ["Col${numKeys+1}": "Suppress Row If No Data For All Cols"]
            headerRow << ["Col16": "RowKey"]
            headerRow << ["Col17": "RowLabels"]
            headerRow << ["Col18": "GroupByRowsConfigId"]
            headerRow << ["Col19": "RowIndex"]
            typesRow << ["Col${numKeys+0}": true]
            typesRow << ["Col${numKeys+1}": true]
            typesRow << ["Col16": "String"]
            typesRow << ["Col17": "String"]
            typesRow << ["Col18": "int"]
            typesRow << ["Col19": "int"]

            rowArr << typesRow
          }

          row << ["Col${numKeys+0}": item.Active]
          row << ["Col${numKeys+1}": item.SuppressIfNoDataForAllCols]
          row << ["Col16": item.RowKey]
          row << ["Col17": item.RowLabels]
          row << ["Col18": item.GroupByRowsConfigId]
          row << ["Col19": item.RowIndex]

          rowArr << row

          firstItem = false
        }

        def headerRowArr = [headerRow]

        // println headerRowArr
        // println rowArr



        // --- result --- //

        outData = [ 
          HeaderColumns: headerRowArr,
          GroupByRowsConfigs: rowArr
        ]
        // println prettyJson(outData)
    }


    is = new ByteArrayInputStream(prettyJson(outData).getBytes("UTF-8"));
    dataContext.storeStream(is, props);
}

private static String prettyJson(def thing) { 
    return JsonOutput.prettyPrint(JsonOutput.toJson(thing))
}
