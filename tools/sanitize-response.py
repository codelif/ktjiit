#!/usr/bin/env python3
# turns a captured {"http", "body"} response into a committable fixture: keeps shape and
# non-identifying values (subject codes, percentages, grades), fakes everything else with a
# per-run salt. pass a third arg for strict mode (profile, bank, fees), which fakes it all.
# usage: sanitize-response.py captured.json out.json [strict]
import json, hashlib, sys, os
SALT = os.urandom(16).hex()
KEEP = {  # values that say nothing about who the student is
 "responseStatus","errors","identifier","registrationcode","registrationdesc","stynumber","currentSem","LTpercantage","Lpercentage",
 "Tpercentage","Ppercentage","Lprepercentage","Tprepercentage","Pprepercentage","individualsubjectcode","subjectcode","subjectdesc",
 "attendancestatus","classtype","datetime","present","audtsubject","credits","minorsubject","remarks","stytype","subjectcomponentcode",
 "totalcreditpoints","registeredCredits","totalreqcredit","earnedcredit","semester","exameventcode","eventfrom","exameventdesc",
 "registrationdatefrom","registrationdateto","sgpa","cgpa","totalcoursecredit","totalearnedcredits","totalpointsecuredcgpa","totalearnedcredit",
 "totalregisteredcredit","prograsivegradepoints","registeredcredit","totalgradepoints","prograsivetotalearnedcredit","earnedgradepoints","prograde",
 "totalpointsecuredsgpa","grade","gradepoint","coursecreditpoint","pointsecured","sgpapoints","cgpapoints","academicyear","branchcode","programcode",
 "programdesc","branchdesc","stymax","clientwiseshowreport","loadGoogleButtonShow","logoClientWise","feeamount","receiveamount","dueamount",
 "waiveramount","refundamount","transferinamount","transferoutamount","currencycode","stytypedesc","amount","roomtype","hosteltypedesc","floor",
 "gender","nationality","category","bloodgroup","batch","sectioncode","branch","admissionyear","institutecode","acholdertypeflag","freezed",
 "percentagemarks","fullmarks","obtainedmarks","yearofpassing","division","qualificationcode","signature","hostellefton","dateofallotment",
 "allotedfromdate","allotedtilldate","regallowdate","quotacode","eventcode","status","msg",
}
def fake(key, v):
    h = hashlib.sha256((SALT + key + str(v)).encode()).hexdigest()
    if isinstance(v, str):
        if not v: return v
        if v.isdigit(): return str(int(h, 16))[:len(v)]
        return ("X" + h.upper())[:max(len(v), 4)] if not key.endswith("id") else ("FAKE" + h.upper())[:len(v)]
    if isinstance(v, bool) or v is None: return v
    if isinstance(v, int): return int(str(int(h, 16))[:max(len(str(v)), 1)])
    return v
STRICT = len(sys.argv) > 3
STRUCT = {"responseStatus", "errors", "identifier", "freezed", "currencycode", "signature", "hostellefton"}
def walk(v, key=""):
    if isinstance(v, dict): return {k: walk(x, k) for k, x in v.items()}
    if isinstance(v, list): return [walk(x, key) for x in v]
    if key in (STRUCT if STRICT else KEEP): return v
    if STRICT and isinstance(v, float): return round(int(hashlib.sha256((SALT + key + str(v)).encode()).hexdigest(), 16) % 10000 / 100, 2)
    if key == "photo": return "/9j/AA=="
    if key in ("name", "studentname", "Username", "employeename", "accountholdername", "fathersname", "mothername"): return "TEST NAME" if v else v
    if key == "loadGoogleClientId": return "000000000000-test.apps.googleusercontent.com"
    if key == "URLapiBase": return v
    return fake(key, v)
src, dst = sys.argv[1], sys.argv[2]
body = json.load(open(src))["body"]
json.dump(walk(body), open(dst, "w"), indent=1, ensure_ascii=False)
