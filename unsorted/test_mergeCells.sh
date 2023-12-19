#!/usr/bin/env bash

script="s200_SET_rowSpanRange_colSpanRange"

fileNames=("IND_ST" "Stab" "IND2" "BA1")
# fileNames=("IND_ST" "Stab" "IND2")
# fileNames=("BA1")
# fileNames=("IND2")

for fileName in "${fileNames[@]}"; do
    echo "----------------------------------------------------------------------"
    echo "                            *** $fileName ***"
    echo "----------------------------------------------------------------------"
    echo
    boomi -s "$script".groovy -d "$fileName"_PreMerge.htm -p "$fileName"_PreMerge.properties -of -e htm -xd -xp
    cp _exec/"$script"_out.dat "$fileName"_Merged.htm
done
