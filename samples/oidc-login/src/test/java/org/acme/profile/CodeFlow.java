package org.acme.profile;

import static io.restassured.RestAssured.given;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.fleettest.Fleet;
import io.restassured.response.Response;

/**
 * The authorization code flow, one step at a time, so that each step can go through a different instance: the
 * redirect to Keycloak, the login at Keycloak, and the redirect back with the code. The URLs Keycloak and the
 * application exchange are already encoded, so RestAssured is told not to encode them again.
 */
final class CodeFlow {

    private CodeFlow() {
    }

    // The redirect to Keycloak, and the state cookie the instance set to recognise the redirect back.
    record Started(String stateCookieName, String stateCookieValue, String keycloakLoginUrl) {
    }

    // Asks for the profile page through the given instance; unauthenticated, it is sent to Keycloak.
    static Started startThrough(Fleet fleet, int instance) {
        fleet.pin(instance);
        Response redirect = given().redirects().follow(false)
                .get("/profile");
        redirect.then().statusCode(302);
        String stateCookie = redirect.cookies().keySet().stream()
                .filter(name -> name.startsWith("q_auth"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no state cookie set, cookies: " + redirect.cookies()));
        return new Started(stateCookie, redirect.cookie(stateCookie), redirect.header("Location"));
    }

    // Logs in as alice on Keycloak's login page and returns where Keycloak sends the browser next: the
    // application's callback, with the code and the state.
    static String loginAtKeycloak(Started started) {
        Response loginPage = given().redirects().follow(false)
                .urlEncodingEnabled(false)
                .get(started.keycloakLoginUrl());
        loginPage.then().statusCode(200);
        Matcher form = Pattern.compile("action=\"([^\"]+)\"").matcher(loginPage.asString());
        if (!form.find()) {
            throw new AssertionError("no login form on the Keycloak page");
        }
        String formAction = form.group(1).replace("&amp;", "&");
        Response loggedIn = given().redirects().follow(false)
                .urlEncodingEnabled(false)
                .cookies(loginPage.cookies())
                .formParam("username", "alice")
                .formParam("password", "alice")
                .post(formAction);
        loggedIn.then().statusCode(302);
        return loggedIn.header("Location");
    }

    // Follows Keycloak's redirect back to the application through the given instance, with the state cookie.
    static Response finishThrough(Fleet fleet, int instance, Started started, String callbackUrl) {
        fleet.pin(instance);
        return given().redirects().follow(false)
                .urlEncodingEnabled(false)
                .cookie(started.stateCookieName(), started.stateCookieValue())
                .get(callbackUrl);
    }

    // The profile page through the given instance, with the session cookies a finished login produced.
    static Response profileThrough(Fleet fleet, int instance, Map<String, String> sessionCookies) {
        fleet.pin(instance);
        return given().redirects().follow(false)
                .cookies(sessionCookies)
                .get("/profile");
    }
}
