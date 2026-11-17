#!/bin/sh

# Defaults
token=""
publicId=""
email=""
reason=""

# Parse named parameters
while [ $# -gt 0 ]; do
    case "$1" in
        --public-id) publicId="$2"; shift 2 ;;
        --token)     token="$2";    shift 2 ;;
        --email)     email="$2";    shift 2 ;;
        --reason)    reason="$2";   shift 2 ;;
        *)           shift ;;
    esac
done

# Validate mandatory parameters
if [ -z "$token" ] || [ -z "$publicId" ] || [ -z "$email" ]; then
    echo "Usage: $0 --token <value> --public-id <value> --email <value>"
    exit 1
fi

authHeader="Authorization: Bearer $token"
contentTypeHeader="Content-Type: application/json"
json="{\"publicId\": \"$publicId\""
json="${json}, \"email\": \"$email\""
if [ -n "$reason" ]; then
    json="${json}, \"reason\": \"$reason\""
fi
json="${json}}"

curl -X POST -H "$authHeader" -H "$contentTypeHeader" -d "$json" http://localhost:8080/api/v1/blocked-registrations
echo
