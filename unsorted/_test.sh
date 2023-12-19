#!/usr/bin/env bash

fileNames=(
    # "J5"
    # "J_TR_2"
    # "J_TRANSPOSE_ERROR"
    # "JEFF_Links_test"
    # "ST2"
    "ST3"
    # "J_IND_Ids"
    "BA2"
    "ba3"
    # "ST2_flat"
    # "summary_flat"
    # "DAP500_flat"
)
scripts=(
    "s120_CREATE_NewPivotedDataConfigs -o -xd -xp"
    "s207_CREATE_RowHeaderConfigs -o -xd -xp"
    "s121_PIVOT_Data -o"
    # "s205_BUILD_Html -xp -of"
    # "s09_APPLY_ColumnWidths -xd -xp -of"
    # "s200_MERGE_Cells_Html -xp -xd -of"
)

for fileName in "${fileNames[@]}"; do

    fileNameOrig="$fileName"

    for scriptWithArgs in "${scripts[@]}"; do

        script="${scriptWithArgs%% *}"
        # echo "$script"
        args="${scriptWithArgs#* }"
        # echo "$args"
        [[ "$script" == "$args" ]] && args=""
        # echo "${#args}"

        if [[ ! "$fileNameOrig" =~ flat || "$script" =~ BUILD_Html ]]; then

            echo
            echo "----------------------------------------------------------------------"
            echo " *  $fileNameOrig   $script   $args"
            echo "----------------------------------------------------------------------"
            echo

            boomi -s "$script".groovy -d "$fileName".dat -p "$fileName".properties "$args"

            fileName="_exec/$script"_out

        fi

    done

    if [[ "$script" =~ Html ]]; then
        cp "$fileName".dat _exec/"$fileNameOrig"_out.htm
        open -a Safari file://"$PWD"/_exec/"$fileNameOrig"_out.htm
    fi

    # cat "$fileName".dat

done
