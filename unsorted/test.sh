#!/usr/bin/env bash

# boomi -s s108_PIVOT_Data.groovy -d StabData.dat -p stab.properties -xp -xd -of
echo "1"
boomi -s s108_PIVOT_Data.groovy -d "$1" -p "$2" -xp -xd -of

echo "2"
boomi -s s106_TRANSFORM_CsvToHtml.groovy -d _exec/s108_PIVOT_Data_out.dat -p _exec/s108_PIVOT_Data_out.properties \
    -xp -xd -of -e htm
# cp _exec/s106_TRANSFORM_CsvToHtml_out.dat test01.htm

echo "3"
boomi -s s09_APPLY_ColumnWidths.groovy -d _exec/s106_TRANSFORM_CsvToHtml_out.dat -p _exec/s106_TRANSFORM_CsvToHtml_out.properties \
    -of -e htm -xd \
    -xp
    # | grep ddp_num
# cp _exec/s09_APPLY_ColumnWidths_out.dat test02.htm

echo "4"
boomi -s s200_SET_rowSpanRange_colSpanRange.groovy -d _exec/s09_APPLY_ColumnWidths_out.dat -p _exec/s09_APPLY_ColumnWidths_out.properties \
    -of -e htm -xd \
    -xp
    # | grep ddp_num
cp _exec/s200_SET_rowSpanRange_colSpanRange_out.dat test03.htm
