import os
import json
import re
from functools import wraps

import requests
import suds
from flask import Flask, request, Response, jsonify, abort
from flask_cors import CORS
from suds.client import Client


app = Flask(__name__)
CORS(app)

IDM_MICROSERVICE_URL = "http://{}:{}/ws/users.wsdl".format(os.environ["IDM_MICROSERVICE_HOST"], os.environ["IDM_MICROSERVICE_PORT"])
DATABASE_MICROSERVICE_API_URL = "http://{}:{}/api".format(os.environ["DATABASE_MICROSERVICE_HOST"], os.environ["DATABASE_MICROSERVICE_PORT"])
SEARCH_MICROSERVICE_API_URL = "http://{}:{}/api".format(os.environ["SEARCH_MICROSERVICE_HOST"], os.environ["SEARCH_MICROSERVICE_PORT"])
NEWSFEED_MICROSERVICE_API_URL = "http://{}:{}/api".format(os.environ["NEWSFEED_MICROSERVICE_HOST"], os.environ["NEWSFEED_MICROSERVICE_PORT"])
JOB_APPLICATION_MICROSERVICE_API_URL = "http://{}:{}/api".format(os.environ["JOB_APPLICATION_MICROSERVICE_HOST"], os.environ["JOB_APPLICATION_MICROSERVICE_PORT"])
NOTIFICATION_MICROSERVICE_API_URL = "http://{}:{}/api".format(os.environ["NOTIFICATION_MICROSERVICE_HOST"], os.environ["NOTIFICATION_MICROSERVICE_PORT"])

CANDIDATE_ROLE_ID = 1
RECRUITER_ROLE_ID = 2
MANAGER_ROLE_ID = 3
ADMIN_ROLE_ID = 4


def extract_token(header):
    result = re.search(r'Bearer (.*)$', header)
    if result is not None:
        return result.group(1)
    else:
        return result


def extract_user_id(url):
    result = re.search(r'users/(\d+)/', url)
    if result is not None:
        return result.group(1)
    else:
        return result


def extract_recruiter_id(url):
    result = re.search(r'recruiters/(\d+)/', url)
    if result is not None:
        return result.group(1)
    else:
        return result


def extract_job_id(url):
    result = re.search(r'jobs/(\d+)/', url)
    if result is not None:
        return result.group(1)
    else:
        return result


def extract_job_application_id(url):
    result = re.search(r'job-applications/([a-zA-Z0-9]+)/', url)
    if result is not None:
        return result.group(1)
    else:
        return result


def login_required(f):
    @wraps(f)
    def decorated_function(*args, **kws):
        if 'Authorization' not in request.headers:
            abort(401)
        user = None
        try:
            idm_client = Client(IDM_MICROSERVICE_URL).service

            auth_header = request.headers['Authorization']
            token = extract_token(auth_header)

            user = idm_client.authorize(token)

            if user is None:
                abort(403)

            user['token'] = token

        except suds.WebFault as e:
            regexp = re.compile("Server raised fault: \'(.*)\'$")
            message = regexp.search(str(e)).group(1)
            status_code, _ = message.split()
            abort(int(status_code))

        except Exception:
            abort(403)

        return f(user, *args, **kws)

    return decorated_function


def owner_required(f):
    @wraps(f)
    def decorated_function(*args, **kws):
        if 'Authorization' not in request.headers:
            abort(401)

        try:
            idm_client = Client(IDM_MICROSERVICE_URL).service

            auth_header = request.headers['Authorization']
            token = extract_token(auth_header)

            reply = idm_client.authorize(token)
            token_user_id = reply['userId']

            path = kws['path']
            request_user_id = extract_user_id(path)

            if str(token_user_id) != str(request_user_id):
                abort(403)

        except suds.WebFault as e:
            regexp = re.compile("Server raised fault: \'(.*)\'$")
            message = regexp.search(str(e)).group(1)
            status_code, _ = message.split()
            abort(int(status_code))

        except Exception:
            abort(403)

        return f(*args, **kws)

    return decorated_function


@app.route("/api/register", methods=['POST'])
def register():
    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service

        request_body = request.get_json()
        email = request_body['email']
        password = request_body['password']
        first_name = request_body['firstName']
        last_name = request_body['lastName']

        reply = idm_client.createCandidate(email, password)
        user_id = reply['id']

        requests.put(f"{DATABASE_MICROSERVICE_API_URL}/users/{user_id}/details",
                     data=json.dumps({'firstName': first_name, 'lastName': last_name}),
                     headers={'content-type': 'application/json'})

        data = {'id': user_id}

        return jsonify(data)
    except suds.WebFault as e:
        print(str(e))
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/login", methods=['POST'])
def login():
    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service
        request_body = request.get_json()
        email = request_body['email']
        password = request_body['password']

        reply = idm_client.login(email, password)
        token = reply['token']
        user = reply['user']
        user_id = user['id']
        user_role = user['userRole']

        data = {
            'token': token,
            'id': user_id,
            'userRole': {'roleId': user_role['role']['id'], 'companyId': user_role['companyId']}
        }

        return jsonify(data)
    except suds.WebFault as e:
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/logout", methods=['POST'])
def logout():
    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service
        request_body = request.get_json()
        token = request_body['token']

        idm_client.logout(token)

        return jsonify(success=True)
    except suds.WebFault as e:
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/recruiters", methods=['POST'])
@login_required
def create_recruiter(user):
    request_body = request.get_json()
    company_id = request_body['companyId']
    email = request_body['email']
    password = request_body['password']

    if user['roleId'] != MANAGER_ROLE_ID or user['companyId'] != company_id:
        abort(403)

    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service

        reply = idm_client.createRecruiter(email, password, company_id)
        recruiter_id = reply['id']

        resp = requests.put(f"{DATABASE_MICROSERVICE_API_URL}/companies/{company_id}/recruiters/{recruiter_id}",
                            headers={'content-type': 'application/json'})
        return to_response(resp)
    except suds.WebFault as e:
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/managers", methods=['POST'])
@login_required
def create_manager(user):
    request_body = request.get_json()
    company_id = request_body['companyId']
    email = request_body['email']
    password = request_body['password']

    if user['roleId'] != ADMIN_ROLE_ID:
        abort(403)

    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service

        reply = idm_client.createManager(email, password, company_id)
        manager_id = reply['id']

        resp = requests.put(f"{DATABASE_MICROSERVICE_API_URL}/companies/{company_id}/managers/{manager_id}",
                            headers={'content-type': 'application/json'})
        return to_response(resp)
    except suds.WebFault as e:
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/companies/<company_id>/recruiters/<recruiter_id>", methods=['DELETE'])
@login_required
def delete_recruiter(user, company_id, recruiter_id):
    if int(user['roleId']) != MANAGER_ROLE_ID or int(user['companyId']) != int(company_id):
        abort(403)

    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service

        token = user['token']
        idm_client.deleteUser(token, recruiter_id)

        resp = requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/companies/{company_id}/recruiters/{recruiter_id}")
        return to_response(resp)
    except suds.WebFault as e:
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/companies/<company_id>/managers/<manager_id>", methods=['DELETE'])
@login_required
def delete_manager(user, company_id, manager_id):
    if user['roleId'] != ADMIN_ROLE_ID:
        abort(403)

    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service

        token = user['token']
        idm_client.deleteUser(token, manager_id)

        resp = requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/companies/{company_id}/managers/{manager_id}")
        return to_response(resp)
    except suds.WebFault as e:
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/users/<user_id>", methods=['DELETE'])
@login_required
def delete_user(user, user_id):
    if user['roleId'] != ADMIN_ROLE_ID:
        abort(403)

    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service

        token = user['token']
        idm_client.deleteUser(token, user_id)

        requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/users/{user_id}/followed-company-industries")
        requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/users/{user_id}/educational-experience")
        requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/users/{user_id}/professional-experience")
        requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/users/{user_id}/languages")
        requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/users/{user_id}/projects")
        requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/users/{user_id}/skills")
        resp = requests.delete(f"{DATABASE_MICROSERVICE_API_URL}/users/{user_id}/details")
        return to_response(resp)
    except suds.WebFault as e:
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/users/<user_id>", methods=['GET'])
@login_required
def get_user(user, user_id):
    print(1)
    if user['roleId'] != ADMIN_ROLE_ID and user['roleId'] != MANAGER_ROLE_ID:
        abort(403)
    print(2)
    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service

        token = user['token']
        reply = idm_client.getUser(token, user_id)
        email = reply['email']

        return {'email': email}
    except suds.WebFault as e:
        print(3)
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()
        abort(int(status_code))


@app.route("/api/users", methods=['GET'])
@login_required
def get_users(user):

    if user['roleId'] != ADMIN_ROLE_ID:
        abort(403)

    try:
        idm_client = Client(IDM_MICROSERVICE_URL).service

        token = user['token']
        reply = idm_client.getUsers(token)

        users = []
        for user in reply:
            user_id = user['id']
            email = user['email']
            user_role = user['userRole']

            users.append({'id': user_id, 'email': email,
                          'userRole': {'roleId': user_role['role']['id'], 'companyId': user_role['companyId']}})

        return jsonify(users)
    except suds.WebFault as e:
        print(str(e))
        regexp = re.compile("Server raised fault: \'(.*)\'$")
        message = regexp.search(str(e)).group(1)
        status_code, _ = message.split()[:2]
        abort(int(status_code))


@app.route("/database-api/<path:path>", methods=["GET", "POST", "PUT", "DELETE"])
@login_required
def database_proxy(user, path):
    user_id = user['userId']
    role_id = user['roleId']

    if path.startswith('users') and request.method != 'GET':
        request_user_id = extract_user_id(path)
        if str(user_id) != str(request_user_id):
            abort(403)

    if path.startswith('jobs') and request.method != 'GET':
        if role_id != RECRUITER_ROLE_ID:
            abort(403)

    if 'companies' in path:
        if 'managers' in path and role_id != ADMIN_ROLE_ID:
            abort(403)

        if request.method == 'POST' and role_id != ADMIN_ROLE_ID:
            abort(403)

        if request.method == 'PUT' and role_id != RECRUITER_ROLE_ID and role_id != MANAGER_ROLE_ID:
            abort(403)

    return forward_request(DATABASE_MICROSERVICE_API_URL, path, request)


@app.route("/job-applications-api/<path:path>", methods=["GET", "POST", "PUT", "DELETE"])
@login_required
def job_applications_proxy(user, path):
    user_id = user['userId']
    role_id = user['roleId']
    company_id = user['companyId']

    request_user_id = extract_user_id(path)
    if request_user_id is not None and str(user_id) != str(request_user_id):
        abort(403)

    request_recruiter_id = extract_recruiter_id(path)
    if request_recruiter_id is not None and role_id != MANAGER_ROLE_ID:
        abort(403)

    job_id = extract_job_id(path)
    if job_id is not None and role_id != RECRUITER_ROLE_ID:
        abort(403)

    job_application_id = extract_job_application_id(path)
    if job_application_id is not None:
        resp = requests.get(f"{JOB_APPLICATION_MICROSERVICE_API_URL}/job-applications/{job_application_id}")
        json_data = json.loads(resp.text)
        job_application_user_id = json_data['userId']
        job_application_recruiter_id = json_data['job']['recruiterId']
        job_application_company_id = json_data['job']['companyId']
        if (role_id == CANDIDATE_ROLE_ID and job_application_user_id != user_id) or \
                (role_id == RECRUITER_ROLE_ID and job_application_recruiter_id != user_id) or \
                (role_id == RECRUITER_ROLE_ID and job_application_company_id != company_id):
            abort(403)

    return forward_request(JOB_APPLICATION_MICROSERVICE_API_URL, path, request)


@app.route("/filter-api/<path:path>", methods=["GET"])
@login_required
def filter_proxy(_, path):
    return forward_request(FILTER_MICROSERVICE_API_URL, path, request)


@app.route("/newsfeed-api/<path:path>", methods=["GET"])
@owner_required
def newsfeed_proxy(path):
    return forward_request(NEWSFEED_MICROSERVICE_API_URL, path, request)


@app.route("/notifications-api/<path:path>", methods=["GET", "PUT", "DELETE"])
@owner_required
def notifications_proxy(path):
    return forward_request(NOTIFICATION_MICROSERVICE_API_URL, path, request)


def forward_request(api_url, path, req):
    resp = None
    url = f"{api_url}/{path}"
    headers = {'content-type': 'application/json'}

    if req.method == "GET":
        resp = requests.get(url=url, params=req.args)
    elif req.method == "POST":
        resp = requests.post(url=url, data=req.data, headers=headers)
    elif req.method == "PUT":
        if len(req.files) != 0:
            request_files = request.files.getlist('file')
            files = []
            for request_file in request_files:
                files.append(('file', (request_file.filename, request_file.read(), request_file.content_type)))
            resp = requests.put(url=url, data=req.data, files=files, headers={'enc-type': 'multipart/form-data'})
        else:
            resp = requests.put(url=url, data=req.data, headers=headers)
    elif req.method == "DELETE":
        resp = requests.delete(url=url, data=req.data, headers=headers)

    return to_response(resp)


def to_response(resp):
    excluded_headers = ["content-encoding", "content-length", "transfer-encoding", "connection"]
    headers = [(name, value) for (name, value) in resp.raw.headers.items() if name.lower() not in excluded_headers]
    response = Response(resp.content, resp.status_code, headers)
    return response


if __name__ == "__main__":
    app.run(debug=True, port=5000)
