#!/bin/bash

# Set up variables for our paths and output file
source_dir="/home/dami/electric-v2-starter-app/src/electric_starter_app/bot"
output_file="combined_code.txt"

# Function to write a file separator to make the output more readable
write_separator() {
    local filename="$1"
    echo -e "\n\n===========================================================" >> "$output_file"
    echo "FILE: $filename" >> "$output_file"
    echo -e "===========================================================\n" >> "$output_file"
}

# Make sure we start with a fresh output file
rm -f "$output_file"
touch "$output_file"

# Add a header with timestamp
echo "Combined Code Files from $source_dir" >> "$output_file"
echo "Generated on $(date)" >> "$output_file"
echo -e "==========================================================\n\n" >> "$output_file"

# Find all files recursively, excluding common version control and temporary files
find "$source_dir" -type f \
    ! -path "*/\.*" \
    ! -name "*.pyc" \
    ! -name "*.pyo" \
    ! -name "*.pyd" \
    ! -name "*.so" \
    ! -name "*.dll" \
    ! -name "*.class" \
    ! -name "*.exe" \
    ! -name "*.cache" \
    ! -name "*.log" \
    -print0 | while IFS= read -r -d $'\0' file; do
    
    # Get the relative path for cleaner output
    relative_path="${file#$source_dir/}"
    
    # Write the file separator with the relative path
    write_separator "$relative_path"
    
    # Append the file contents
    cat "$file" >> "$output_file"
done

# Add a footer with file count and size
echo -e "\n\n===========================================================" >> "$output_file"
echo "End of combined files" >> "$output_file"
echo "Total files: $(find "$source_dir" -type f ! -path "*/\.*" | wc -l)" >> "$output_file"
echo "Total size: $(du -h "$output_file" | cut -f1)" >> "$output_file"
echo "==========================================================" >> "$output_file"

echo "All code has been combined into $output_file"