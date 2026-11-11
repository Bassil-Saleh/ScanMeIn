#!/bin/sh

# Defaults
email=""
publicId=""

# Parse named parameters
while [ $# -gt 0 ]; do
	case "$1" in
		--email)     email="$2";    shift 2 ;;
        --public-id) publicId="$2"; shift 2 ;;
		*)           shift ;;
	esac
done

# Validate mandatory parameters
if [ -z "$email" ] || [ -z "$publicId" ]; then
	echo "Usage: $0 --email <value> --public-id <value>"
	exit 1
fi

contentTypeHeader="Content-Type: application/json"
url="http://localhost:8080/api/v1/attendees/resends/${publicId}"
json="{\"email\": \"$email\"}"

curl -X POST -H "$contentTypeHeader" -d "$json" "$url"
echo