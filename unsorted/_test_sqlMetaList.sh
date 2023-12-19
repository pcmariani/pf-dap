#!/usr/bin/env bash

fileNames=(
	"sqlMetaList"
	# "PHRD_SqlMeta"
	# "IND_Meta"
	# "eln_sqlMeta"
	# "phrd_091"
	# "snowflake_meta"
)
scripts=(
	"s130_SqlMeta -o -xp -xd"
	"s130_SqlListOfValues -o -xp -xd"
)

i=1
for fileName in "${fileNames[@]}"; do

	fileNameOrig="$fileName"

	for scriptWithArgs in "${scripts[@]}"; do

		script="${scriptWithArgs%% *}"
		# echo "$script"
		args="${scriptWithArgs#* }"
		# echo "$args"
		[[ "$script" == "$args" ]] && args=""
		# echo "${#args}"

		echo "----------------------------------------------------------------------"
		echo " * $i *  $fileNameOrig   $script   $args"
		echo "----------------------------------------------------------------------"

		boomi -s "$script".groovy -d "$fileName".dat -p "$fileName".properties "$args" "$@"

		fileName="_exec/$script"_out

	done

	echo
	((i++))

done
