#!/bin/sh

# Defaults
token=""
publicId=""
emails=""

# Parse named parameters
while [ $# -gt 0 ]; do
    case "$1" in
        --public-id) publicId="$2"; shift 2 ;;
        --token)     token="$2";    shift 2 ;;
        --email)
            if [ -z "$emails" ]; then
                emails="$2"
            else
                emails="$emails $2"
            fi
            shift 2
            ;;
        *)           shift ;;
    esac
done

# Prevent glob expansion of unquoted emails in $emails
set -f
# Validate mandatory parameters
if [ -z "$token" ] || [ -z "$publicId" ] || [ -z "$emails" ]; then
    echo "Usage: $0 --token <value> --public-id <value> --email <value> ..."
    set +f
    exit 1
fi

authHeader="Authorization: Bearer $token"
contentTypeHeader="Content-Type: application/json"
json="{\"publicId\": \"$publicId\""
json="${json}, \"emails\": ["
separator=""
for email in $emails; do
    json="${json}${separator}\"${email}\""
    separator=","
done
set +f
json="${json}]}"

curl -X POST -H "$authHeader" -H "$contentTypeHeader" -d "$json" http://localhost:8080/api/v1/blocked-registrations
echo
