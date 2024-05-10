import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
  InputStream is = dataContext.getStream(i);
  Properties props = dataContext.getProperties(i);

  /* INPUTS */

  def categoryRowPrependText = props.getProperty("document.dynamic.userdefined.ddp_categoryRowPrependText") ?: ""
  int numCategoryRows = (props.getProperty("document.dynamic.userdefined.ddp_numCategoryRows") ?: "0") as int
  numCategoryRows = 2

  /* LOGIC */

  def root = new XmlParser().parse(is)
  root.'**'.findAll() { node -> node.name() == "table" }.eachWithIndex() { table, t ->

    // --- vars for loop --- //
    // when there is a rowspan, save it and then decriment it for each row that is spanned
    ArrayList<Integer> rowSpansSavedArr = [0] * numCategoryRows
    ArrayList<Integer> rowSpansSavedArrPrev = [0] * numCategoryRows
    Boolean firstDataRow = true
    int rowSpanValSaved = 0
    int numRowsAdded = 0
    int numCols = 0
    ArrayList outArr = []
    // println rowSpansSavedArr

    // --- loop though TRs --- //
    table.tr.eachWithIndex { tr, f ->

      // --- vars for loop --- //
      // save the node
      ArrayList rowCellsArr = tr.children()[0..numCategoryRows - 1]
      // println rowCellsArr
      ArrayList rowSpansArr = rowCellsArr.collect{ it.@rowSpan ? it.@rowSpan as int : 0 }
      // println rowSpansArr
      Boolean markedForAction = false
      Boolean firstDataCol = true

      // --- loop though cells (TH/TD) --- //
      rowCellsArr.eachWithIndex{ cell, c ->
        if (!rowSpansSavedArr[c]) {
          markedForAction = true
          if (rowSpansArr[c] != 0) {
            rowSpansSavedArr[c] = rowSpansArr[c] - 1
          } else {
            rowSpansSavedArr[c] = rowSpansArr[c]
          }
        }
        else if (numCategoryRows > 1 && rowSpansSavedArr[c] && rowSpansArr[c] != 0) {
          markedForAction = true
          rowSpansSavedArr[c+1] = rowSpansArr[c]
          rowSpansSavedArr[c]--
        }
        else {
          rowSpansSavedArr[c]--
        }
      }



      if (markedForAction) {

        if (rowCellsArr[0].name() == "td") {

          // --- set vars for loop --- //
          int countAdded = 0

          // --- loop through cells (TH/TD) again --- //
          rowSpansSavedArrPrev.eachWithIndex{ span, s ->
            if (span == 0) {
              outArr[s] = rowCellsArr[countAdded].value()[0]
              countAdded++
            }
          }

          if (firstDataRow && firstDataCol) {
            numCols = table.tr[f].children().size() - numCategoryRows
            firstDataRow = false
            firstDataCol = false
          }
          def newtr = new Node(table, 'tr')
          def newtd = new Node(newtr,
            'td',
            [
              colSpan: numCols,
              style: "width:100%; font-weight:bold; text-align:center;",
              id: rowCellsArr[rowSpansSavedArrPrev.count(0)-1].@id
            ],
            categoryRowPrependText + outArr.join(" - ")
          )
          table.remove(newtr)
          table.children().add(f+numRowsAdded, newtr)
          numRowsAdded++
        }

        (0..rowSpansSavedArrPrev.count(0)-1).each {
          rowCellsArr[it].replaceNode{}
        }

      }

      // --- for debugging --- //
      if (markedForAction) {  // <-- comment line to see all
        println nicetable(
          f,
          "  ",
          [3, 4, 10, 10, 15, rowCellsArr.size()*26, 7, 30],
          ["ROW", "TYPE", "RSPAN", "RSPAN-SAVE", "RSPAN-SAVE-PREV", "ROW-VALUES", "MARKED", "OUT"],
          [
            f,
            rowCellsArr[0].name(),
            rowSpansArr.sum() != 0 ? rowSpansArr.join(",") : "",
            rowSpansSavedArr.join(","),
            rowSpansSavedArrPrev.join(","),
            rowCellsArr.collect{ it.value()[0] }.join("  .  "),
            markedForAction ? "x" : "",
            outArr.join("  .  ")
          ]
        )
      }  // <-- comment line to see all

      rowSpansSavedArrPrev = rowSpansSavedArr.clone()
    }

  }

  /* OUTPUT */
  String outData
  outData = groovy.xml.XmlUtil.serialize(root).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim() //.replaceAll(/<tr\s*?\/\s*>/,"")
  is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
  dataContext.storeStream(is, props);
}

private String nicetable(int index, String delimiter, ArrayList widthsArr, ArrayList headersArr, ArrayList valuesArr) {
  StringBuilder outStr = new StringBuilder()
  
  def processArray = { ArrayList arr, String padChar ->
    def lineArr = [""] * arr.size()
    arr.eachWithIndex{ val, i ->
      int padSize = widthsArr[i] - val.toString().size()
      if (val instanceof Integer) {
        lineArr[i] = (padChar * (padSize > 0 ? padSize : 0)) + val.toString()
      } else {
        lineArr[i] = val + (padChar * (padSize > 0 ? padSize : 0))
      }
    }
    return lineArr
  }

  if (index == 0) {
    outStr.append(processArray(headersArr, ".").join("." * delimiter.size()) + System.lineSeparator())
  }
  outStr.append(processArray(valuesArr, " ").join(delimiter))

  return outStr.toString()
}

