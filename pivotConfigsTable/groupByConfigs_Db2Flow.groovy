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
        // --- calculate numKeysCols --- //

        ArrayList numKeysColsArr = []
        root.each { item ->
          numKeysColsArr << item.RowKey.split(DBIFS).size()
        }
        // println numKeysColsArr

        int numKeysCols
        if (numKeysColsArr.unique().size() == 1) {
          numKeysCols = numKeysColsArr.unique()[0]
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

          def rowKeyArr = item.RowKey.split(DBIFS)
          def rowLabelsArr = item.RowLabels.split(DBIFS)

          rowKeyArr.eachWithIndex { key, k ->
            if (firstItem) {
              headerRow << ["Col$k": "Column Label ${k+1}"]
              typesRow << ["Col$k": "String"]
            }
            if (key) {
              row << ["Col$k": rowLabelsArr[k]]
            }
          }


          for(int k = 0; k < numKeysCols; k++ ) {
            if (firstItem) {
              headerRow << ["Col$k": "Row Label ${k+1}"]
              typesRow << ["Col$k": "String"]
            }

            row << ["Col$k": rowLabelsArr[k]]
          }

          if (firstItem) {
            headerRow << [
              "Col9": "Active",
              "Col10": "Suppress Row If No Data For All Cols",
              "Col16": "RowKey",
              "Col17": "RowLabels",
              "Col18": "GroupByRowsConfigId",
              "Col19": "RowIndex"
            ]
            typesRow << [
              "Col9": true,
              "Col10": true,
              "Col16": "String",
              "Col17": "String",
              "Col18": "int",
              "Col19": "int"
            ]

            rowArr << typesRow
          }

          row << [
            "Col9": item.Active,
            "Col10": item.SuppressIfNoDataForAllCols,
            "Col16": item.RowKey,
            "Col17": item.RowLabels,
            "Col18": item.GroupByRowsConfigId,
            "Col19": item.RowIndex
          ]

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
