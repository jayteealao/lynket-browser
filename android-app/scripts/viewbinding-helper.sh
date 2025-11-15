#!/bin/bash

# ViewBinding Migration Helper Script
# This script helps with the mechanical parts of ViewBinding migration
# IMPORTANT: Always review changes manually before committing!

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
LYNKET_SRC="$PROJECT_ROOT/lynket/src/main/java"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

function print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

function print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

function print_error() {
    echo -e "${RED}✗ $1${NC}"
}

function print_info() {
    echo -e "$1"
}

# Function to find all files with synthetic imports
function find_synthetic_files() {
    print_info "Searching for files with kotlin-android-extensions..."
    grep -r "import kotlinx.android.synthetic" "$LYNKET_SRC" -l 2>/dev/null || true
}

# Function to count remaining files
function count_remaining() {
    local count=$(find_synthetic_files | wc -l)
    echo "$count"
}

# Function to show migration progress
function show_progress() {
    local total=32
    local remaining=$(count_remaining)
    local completed=$((total - remaining))
    local percent=$((completed * 100 / total))

    print_info "==================================="
    print_info "ViewBinding Migration Progress"
    print_info "==================================="
    print_info "Total files:      $total"
    print_info "Completed:        $completed"
    print_info "Remaining:        $remaining"
    print_info "Progress:         $percent%"
    print_info "==================================="
}

# Function to validate a layout file exists
function validate_layout() {
    local layout_name=$1
    local layout_path="$PROJECT_ROOT/lynket/src/main/res/layout/${layout_name}.xml"

    if [ -f "$layout_path" ]; then
        return 0
    else
        print_warning "Layout file not found: $layout_name.xml"
        return 1
    fi
}

# Function to generate binding class name from layout name
function layout_to_binding() {
    local layout_name=$1
    # Convert snake_case to PascalCase and add Binding suffix
    # e.g., activity_main -> ActivityMainBinding
    echo "$layout_name" | sed 's/_\([a-z]\)/\U\1/g' | sed 's/^\([a-z]\)/\U\1/' | sed 's/$/Binding/'
}

# Function to extract layout name from setContentView or inflate call
function extract_layout_name() {
    local file=$1

    # Try to find setContentView(R.layout.xxx)
    local layout=$(grep -oP 'setContentView\(R\.layout\.\K[a-zA-Z_]+' "$file" | head -1)

    if [ -z "$layout" ]; then
        # Try to find inflate(R.layout.xxx)
        layout=$(grep -oP 'inflate\(R\.layout\.\K[a-zA-Z_]+' "$file" | head -1)
    fi

    echo "$layout"
}

# Function to detect if file is Activity or Fragment
function detect_type() {
    local file=$1

    if grep -q "class.*: .*Activity" "$file"; then
        echo "Activity"
    elif grep -q "class.*: .*Fragment" "$file"; then
        echo "Fragment"
    elif grep -q "class.*: EpoxyModelWithHolder" "$file"; then
        echo "EpoxyModel"
    elif grep -q "class.*ViewHolder" "$file"; then
        echo "ViewHolder"
    else
        echo "Unknown"
    fi
}

# Function to create a backup
function backup_file() {
    local file=$1
    cp "$file" "${file}.bak"
    print_success "Created backup: ${file}.bak"
}

# Function to analyze a single file
function analyze_file() {
    local file=$1
    local filename=$(basename "$file")
    local type=$(detect_type "$file")
    local layout=$(extract_layout_name "$file")
    local binding=$(layout_to_binding "$layout")

    print_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    print_info "File: $filename"
    print_info "Type: $type"
    print_info "Layout: ${layout:-Not found}"
    print_info "Binding Class: ${binding:-Unknown}"

    if [ ! -z "$layout" ]; then
        if validate_layout "$layout"; then
            print_success "Layout file exists"
        fi
    fi

    # Count synthetic usages
    local synthetic_count=$(grep -c "^import kotlinx.android.synthetic" "$file" 2>/dev/null || echo "0")
    print_info "Synthetic imports: $synthetic_count"

    print_info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# Function to list all files needing migration
function list_files() {
    print_info "Files needing ViewBinding migration:\n"

    local count=1
    while IFS= read -r file; do
        local filename=$(basename "$file")
        local type=$(detect_type "$file")
        printf "%2d. %-50s [%s]\n" "$count" "$filename" "$type"
        count=$((count + 1))
    done < <(find_synthetic_files)
}

# Function to remove synthetic imports from a file
function remove_synthetics() {
    local file=$1

    print_info "Removing synthetic imports from $(basename "$file")..."

    # Remove synthetic import lines
    sed -i '/^import kotlinx\.android\.synthetic/d' "$file"

    # Remove clearFindViewByIdCache if present
    sed -i '/clearFindViewByIdCache()/d' "$file"

    print_success "Removed synthetic imports"
}

# Main command dispatcher
case "${1:-help}" in
    progress)
        show_progress
        ;;
    list)
        list_files
        ;;
    analyze)
        if [ -z "$2" ]; then
            print_error "Usage: $0 analyze <file-path>"
            exit 1
        fi
        analyze_file "$2"
        ;;
    clean-synthetics)
        if [ -z "$2" ]; then
            print_error "Usage: $0 clean-synthetics <file-path>"
            exit 1
        fi
        backup_file "$2"
        remove_synthetics "$2"
        ;;
    help|*)
        print_info "ViewBinding Migration Helper"
        print_info ""
        print_info "Usage: $0 <command> [arguments]"
        print_info ""
        print_info "Commands:"
        print_info "  progress              Show migration progress"
        print_info "  list                  List all files needing migration"
        print_info "  analyze <file>        Analyze a specific file"
        print_info "  clean-synthetics <f>  Remove synthetic imports from file (creates backup)"
        print_info "  help                  Show this help message"
        print_info ""
        print_info "Examples:"
        print_info "  $0 progress"
        print_info "  $0 list"
        print_info "  $0 analyze lynket/src/main/java/arun/com/chromer/home/HomeActivity.kt"
        print_info "  $0 clean-synthetics lynket/src/main/java/arun/com/chromer/about/AboutFragment.kt"
        ;;
esac
