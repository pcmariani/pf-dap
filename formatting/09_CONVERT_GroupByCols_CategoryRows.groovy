/* In pivoted tables, the left column or columns are the GroupBy columns. They are
 * like the category labels for their row. Sometimes the user wants to put that category
 * label above their rows instead of on the side. This script allows that for as many
 * GroupBy column on the left as is specified. The configuration is in:
 *
 *   Source.Pivot_GroupByToCategoryRowsConfig
 *
 * Examples:
 *
 *   ddp_numGroupByColsToCategoryRows = 1
 *
 *     +----------+--------------+----+----+----+----+       +--------------+----+----+----+----+
 *     | Category | Sub-category | t1 | t2 | t3 | t4 |       | Sub-category | t1 | t2 | t3 | t4 |
 *     +----------+--------------+----+----+----+----+       +--------------+----+----+----+----+
 *     | First    | a            | x  | x  | x  | x  |       |               First              |
 *     +          +--------------+----+----+----+----+       +--------------+----+----+----+----+
 *     | First    | bb           | x  | x  | x  | x  |       | a            | x  | x  | x  | x  |
 *     +----------+--------------+----+----+----+----+  -->  +--------------+----+----+----+----+
 *     | Second   | a            | x  | x  | x  | x  |  -->  | bb           | x  | x  | x  | x  |
 *     +          +--------------+----+----+----+----+       +--------------+----+----+----+----+
 *     | Second   | a            | x  | x  | x  | x  |       |              Second              |
 *     +          +--------------+----+----+----+----+       +--------------+----+----+----+----+
 *     | Second   | bb           | x  | x  | x  | x  |       | a            | x  | x  | x  | x  |
 *     +----------+--------------+----+----+----+----+       +--------------+----+----+----+----+
 *                                                           | a            | x  | x  | x  | x  |
 *                                                           +--------------+----+----+----+----+
 *                                                           | bb           | x  | x  | x  | x  |
 *                                                           +--------------+----+----+----+----+
 *
 *   ddp_numGroupByColsToCategoryRows = 2
 *
 *     +----------+--------------+----+----+----+----+       +----+----+----+----+
 *     | Category | Sub-category | t1 | t2 | t3 | t4 |       | t1 | t2 | t3 | t4 |
 *     +----------+--------------+----+----+----+----+       +----+----+----+----+
 *     | First    | a            | x  | x  | x  | x  |       |     First - a     |
 *     +          +--------------+----+----+----+----+       +----+----+----+----+
 *     | First    | bb           | x  | x  | x  | x  |       | x  | x  | x  | x  |
 *     +----------+--------------+----+----+----+----+  -->  +----+----+----+----+
 *     | Second   | a            | x  | x  | x  | x  |  -->  |     First - bb    |
 *     +          +              +----+----+----+----+       +----+----+----+----+
 *     | Second   | a            | x  | x  | x  | x  |       | x  | x  | x  | x  |
 *     +          +--------------+----+----+----+----+       +----+----+----+----+
 *     | Second   | bb           | x  | x  | x  | x  |       |    Second - a     |
 *     +----------+--------------+----+----+----+----+       +----+----+----+----+
 *                                                           | x  | x  | x  | x  |
 *                                                           +----+----+----+----+
 *                                                           | x  | x  | x  | x  |
 *                                                           +----+----+----+----+
 *                                                           |    Second - bb    |
 *                                                           +----+----+----+----+
 *                                                           | x  | x  | x  | x  |
 *                                                           +----+----+----+----+
 *
 */

import java.util.Properties;
import java.io.InputStream;
import groovy.json.JsonSlurper
import groovy.json.JsonOutput;

for( int i = 0; i < dataContext.getDataCount(); i++ ) {
  InputStream is = dataContext.getStream(i);
  Properties props = dataContext.getProperties(i);

  /* INPUTS */

  def sourcesJson = props.getProperty("document.dynamic.userdefined.ddp_Sources")
  def config = new JsonSlurper().parseText(sourcesJson).Records[0]?.PivotGroupByCategoryRows
  // println prettyJson(config)

  // HACK FOR TESTING - remove once UI and API are complete {{{
  // Boolean isPHRDReport = (props.getProperty("document.dynamic.userdefined.ddp_IsPHRDReport") ?: "false").toBoolean()
  // if (isPHRDReport) {
  //   config = [
  //     NumGroupByColsToConvert: 1,
  //     TextToPrepend: "",
  //     Delimiter: " - "
  //   ]
  // }
  // }}} END HACK

  int numGroupByColsToConvert = config?.NumGroupByColsToConvert

  if (numGroupByColsToConvert && numGroupByColsToConvert != 0) {

    /* LOGIC */

    def root = new XmlParser().parse(is)

    root.'**'.findAll() { node -> node.name() == "table" }.eachWithIndex() { table, t ->

      // --- vars for loop --- //
      ArrayList resultStringArr = []
      ArrayList rowSpansSavedArr = [0] * numGroupByColsToConvert
      ArrayList rowSpansSavedArrPrev = [0] * numGroupByColsToConvert
      Boolean firstDataRow = true
      int numRowsAdded = 0
      int numCols = 0

      // --- loop though TRs --- //
      table.tr.eachWithIndex { tr, f ->

        // --- vars for loop --- //
        // save numGroupByColsToConvert number of cells
        ArrayList cellsArr = tr.children()[0..numGroupByColsToConvert - 1]
        // for those cells, save the rowSpan attributes
        ArrayList rowSpansArr = cellsArr.collect{ it.@rowSpan ? it.@rowSpan as int : 0 }
        Boolean markedForAction = false
        Boolean firstDataCol = true

        // --- loop though cells (TH/TD) --- //
        cellsArr.eachWithIndex{ cell, c ->
          /* Let's just be honest here... I don't exaclty know how this works.
          *
          * The html tables coming into this script have gone through a process of
          * cell merging, so that GroupBy columns merged vertiacally. In the diagrams
          * above, the GroupBy categories "First", "Second" and "a" are all merged.
          * That means that on the first row of the merged cell "First", there is TD 
          * who's value is "First" and that TD has an attribute rowSpan=2. It also
          * means that the on the second row of the merged cell, there is no TD with
          * a value of "First", because it was spanned. The first TD on that row has
          * a value of "bb".
          *
          * The challenge here is to represent the rowspans for each GroupBy column
          * from left to right. If the left most merged cell is "First" and the second
          * is "a", we must preserve the span of "First" even though, when we encounter
          * the rowspan for "a", there is no TD present for "First" becuase it was
          * spanned on that row. In other words, don't allow the rowspan of "a" to
          * take the first position if we're still within the span of "First".
          *
          * Roughly speaking, a row needs to be marked for action if:
          * - the current cell is not within a rowspan
          * - the cell to the left is within a rowspan, but the current cell is not
          */

          // are we not in a rowspan for this cell?
          if (!rowSpansSavedArr[c]) {
            markedForAction = true
            // does this cell not have a rowspan?
            if (rowSpansArr[c] != 0) {
              rowSpansSavedArr[c] = rowSpansArr[c] - 1
            } else {
              rowSpansSavedArr[c] = rowSpansArr[c]
            }
          }
          // are we in a rowspan for this cell and does this cell not have a rowspan?
          else if (numGroupByColsToConvert > 1 && rowSpansSavedArr[c] && rowSpansArr[c] != 0) {
            markedForAction = true
            // since we're in a rowpan the spanned TD doesn't exist. So the cell we're
            // evaluating actually belongs to the next column if you're looking at the 
            // table visually
            rowSpansSavedArr[c+1] = rowSpansArr[c]
            // decrement the rowpan so that it will be zero when the span is complete as
            // we're looping down the rows
            rowSpansSavedArr[c]--
          }
          else {
            // same decrement
            rowSpansSavedArr[c]--
          }
        }


        /* Actions to be taken are:
        * - remove the GroupBy columns we want to convert
        * - for each GroupBy value that's merged vertically, e.g. "First", "Second" or
        *   "a", create a row above with the value of that merged cell. If we are
        *   converting more that one GroupBy column, concatenate all the values for
        *   merged GroupBy cells in that row
        */
        if (markedForAction) {

          if (cellsArr[0].name() == "td") {

            // --- set vars for loop --- //
            int countAdded = 0

            // --- loop through cells (TD) --- //
            rowSpansSavedArrPrev.eachWithIndex{ span, s ->
              /* 0's in the rowSpansSavedArrPrev represent the position to put the value
              * of our cell within the result string. E.g. [0,0] means put the value of
              * the first cell into the first position, and put the value of the second
              * cell into the second position. [8,0] means, skip the first cell and put
              * the value of the second cell into the second position. The first position
              * will be filled because will take the value of the previous resultsStringArr
              * of the same position, because the resultStringArr is not re-initialized 
              * (cleared) for each row. Thus, if we're converting more than one GroupBy
              * col, the first col value will persist until it's replaced by the next
              * value. E.g. "First" will persist in the first position, until it's replaced
              * by "Second".
              */
              if (span == 0) {
                resultStringArr[s] = cellsArr[countAdded].value()[0]
                countAdded++
              }
            }

            // get the width of the table, but only once (the first row and first cell in
            // that row)
            if (firstDataRow && firstDataCol) {
              numCols = table.tr[f].children().size() - numGroupByColsToConvert
              firstDataRow = false
              firstDataCol = false
            }
            // create the new XML nodes for the row (TR) and cells within that row (TD)
            def newtr = new Node(table, 'tr')
            def newtd = new Node(newtr,
              'td',
              [
                colSpan: numCols,
                style: "width:100%; font-weight:bold; text-align:center;",
                id: cellsArr[rowSpansSavedArrPrev.count(0)-1].@id
              ],
              ( config.TextToPrepend ?: "" ) + resultStringArr.join(config.Delimiter)
            )
            // The best we can do above is append the new row (tr) to the bottom of the parent table.
            // We need to basically cut (remove) it and paste (add) it at the correct row index.
            // We need the numRowsAdded so that we don't keep pasting the new rows at the same index.
            table.remove(newtr)
            table.children().add(f+numRowsAdded, newtr)
            numRowsAdded++
          }

          // Just like above, 0's in rowSpansSavedArrPrev represent the positions of the cells
          // we need to remove. Here we loop for as many 0' and delete that many cells from the
          // cellsArr from left to right.
          (0..rowSpansSavedArrPrev.count(0)-1).each {
            cellsArr[it].replaceNode{}
          }

        }

        // // --- for debugging --- //
        // if (markedForAction) {  // <-- comment line to see all
        //   println niceTableForDebugging(
        //     f,
        //     "  ",
        //     [3, 3, 10, 10, 15, cellsArr.size()*26, 7, 30],
        //     ["ROW", "TAG", "RSPAN", "RSPAN-SAVE", "RSPAN-SAVE-PREV", "ROW-VALUES", "MARKED", "RESULT-STRING"],
        //     [
        //       f,
        //       cellsArr[0].name(),
        //       rowSpansArr.sum() != 0 ? rowSpansArr.join(",") : "",
        //       rowSpansSavedArr.join(","),
        //       rowSpansSavedArrPrev.join(","),
        //       cellsArr.collect{ it.value()[0] }.join("  .  "),
        //       markedForAction ? "x" : "",
        //       config.TextToPrepend + resultStringArr.join(config.Delimiter)
        //     ]
        //   )
        // }  // <-- comment line to see all

        rowSpansSavedArrPrev = rowSpansSavedArr.clone()
      }

    }

    /* OUTPUT */

    String outData
    outData = groovy.xml.XmlUtil.serialize(root).replaceFirst("\\<\\?xml(.+?)\\?\\>", "").trim()
    is = new ByteArrayInputStream(outData.toString().getBytes("UTF-8"));
  }

  dataContext.storeStream(is, props);
}

private String prettyJson(def thing) { return JsonOutput.prettyPrint(JsonOutput.toJson(thing)) }

private String niceTableForDebugging(int index, String delimiter, ArrayList widthsArr, ArrayList headersArr, ArrayList valuesArr) {
  StringBuilder outStr = new StringBuilder()
  def processArray = { ArrayList arr ->
    def lineArr = [""]*arr.size()
    arr.eachWithIndex{ val, i ->
      int padSize = widthsArr[i] - val.toString().size()
      if (val instanceof Integer) {
        lineArr[i] = (" "*(padSize > 0 ? padSize : 0)) + val.toString()
      } else {
        lineArr[i] = val + (" "*(padSize > 0 ? padSize : 0))
      }
    }
    return lineArr
  }
  if (index == 0) {
    outStr.append(processArray(headersArr).join(" "*delimiter.size()) + System.lineSeparator())
    outStr.append(processArray(headersArr.collect{ "-"*it.size() }).join(delimiter) + System.lineSeparator())
  }
  outStr.append(processArray(valuesArr).join(delimiter))
  return outStr.toString()
}

