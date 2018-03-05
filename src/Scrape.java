import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

public class Scrape {
    private static String _currentCaseID = "";
    private static HashMap<String, HashMap> results;

    /**
     * This is the main function that is in charge of driving the automation
     *
     * @param args The arguments for the test - but we won't be using them.
     */
    public static void main(String[] args) {
        int stateCount = 30;
        ChromeDriver driver = new ChromeDriver();
        WebDriverWait wait = new WebDriverWait(driver, 600);

        while (stateCount <= 30) {
            // Reset results variable at the top of the loop for new state
            results = new HashMap<String, HashMap>();

            // Use Chrome driver to navigate to NamUs
            driver.navigate().to("https://www.findthemissing.org/en");

            //
            driver.findElement(By.id("search_Circumstances.StateLKA")).click();
            // Find the option tag where the value is equal to whatever the state count at the time of the loop is
            WebElement option = driver.findElement(By.xpath("//option[@value=\"" + stateCount + "\"]"));
            // Create a type that will later be used to create a file where our scraping output will be written to.
            String outputFileName = option.getText().toLowerCase().replaceAll(" ", "-") + "-output.json";
            // Click the state count
            option.click();

            // Search the DOM for a name value of commit and click that
            driver.findElement(By.xpath("//input[@name=\"commit\"]")).click();

            // Wait for list to appear with at least one returned missing person case
            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("table#list > tbody > tr:nth-child(2)")));

            // Select first missing person case in the list
            driver.findElement(By.cssSelector("table#list > tbody > tr:nth-child(2)")).click();

            // Process the first account before skipping to the next one
            _processAccount(driver, wait);

            // While the next case button is available continue to click it until it's no longer available
            // i.e. you reached the end of the cases
            /*while (driver.findElements(By.id("NextCase")).size() > 0) {
                driver.findElement(By.id("NextCase")).click();
                _processAccount(driver, wait);
            }*/

            // Once you've reached the end of the cases increment the state count by one
            stateCount += 1;

            // When all is said and done then turn the Java object into JSON using JSONObject
            try {
                // Take the results and turn them into a JSON string then take that and turn it into a JSON object
                String jsonString = JSONObject.valueToString(results);
                JSONObject jsonObject = new JSONObject(jsonString);

                // Write the JSON to the appropriate state file name
                FileWriter fileWriter = new FileWriter(outputFileName);
                fileWriter.write(jsonObject.toString(4));
                fileWriter.flush();
            } catch (JSONException e) {
                // If there is an error placing the java object into JSON print the strace trace
                e.printStackTrace();
            } catch (IOException e) {
                // If there is an error placing the JSON into a file print the stack trace
                e.printStackTrace();
            }
        }
        driver.quit();

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //                                                 HELPER FUNCTIONS                                               //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This function processes the current account/case. It is the one that is in charge of
     * opeing each section and calling child functions to get the specific data. It also
     * keeps track of all the results for a given account.
     *
     * @param driver
     * @param wait
     */
    private static void _processAccount(ChromeDriver driver, WebDriverWait wait) {
        HashMap<String, HashMap> accountResults = new HashMap<String, HashMap>();

        // Wait for the tsb title to change and the case info header to load
        if ("1".equals(_currentCaseID)) {
            _sleep(2000);
        } else if (!"".equals(_currentCaseID)) {
            wait.until(ExpectedConditions.not(ExpectedConditions.titleContains(_currentCaseID)));
        }
        // Wait until the Case Information appears
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("case_information")));
        // There is a 600ms animation on the case info that you have to account for when automating (thanks jQuery)
        _sleep(600);

        // Parse the case id from after the number sign (do not include the #)
        int hashLoc = driver.getTitle().lastIndexOf('#') + 1;
        // Remove the space before the case id and assign the number to the caseID variable
        String caseID = driver.getTitle().substring(hashLoc).trim();

        // Store off the case ID for next run to see if the header changes
        _currentCaseID = caseID;

        // Parse out each page of data by calling the helper functions
        accountResults.put("caseInfo", _utilParseTableRows(driver, By.cssSelector("#case_information table tr")));
        accountResults.put("circumstances", _parseCircumstances(driver));
        accountResults.put("characteristics", _parsePhysicalCharacteristics(driver));
        accountResults.put("photos", _parsePhotos(driver));
        accountResults.put("investigatingAgency", _parseInvestigatingAgency(driver));
        _parseContacts(driver, accountResults);

        // Add the current account results to the full list of accounts
        results.put(caseID, accountResults);

        // Switch back to case information and wait for animation to finish before moving on
        //driver.executeScript("change_block(\"case_information\")");
        //_sleep(1200);
    }

    /**
     * This function takes in the rows of a table and parses out the individual TDs for the key and value
     * it then returns a HashMap of the key value pairs
     *
     * @param driver WebDriver instance
     * @param location Location selector thing to find the rows
     * @return HashMap of key values
     */
    private static HashMap<String, String> _utilParseTableRows(ChromeDriver driver, By location) {
        HashMap<String, String> info = new HashMap<String, String>();

        // Use the given location guy to find all rows to look at
        List<WebElement> elements = driver.findElements(location);
        Iterator<WebElement> elementsIterator = elements.iterator();

        // Loop over each row of data
        while (elementsIterator.hasNext()) {
            WebElement row = elementsIterator.next();
            // For each row get the label and the value
            String key;
            String value;

            // If there is another table nested in this row then skip for now
            if (row.findElements(By.tagName("table")).size() > 0) {
                continue;
            }

            // Check to see how man cells are in the row since it changes how to find the data
            int tdCount = row.findElements(By.tagName("td")).size();
            // Some tables will have a header, ignore the header
            if (tdCount == 1) {
                continue;
            } else if (tdCount == 3) {
                // Some tables have an initial first data element we don't care about (like check boxes) skip those
                //key = row.findElement(By.cssSelector("td:nth-child(2)")).getText();
                //value = row.findElement(By.cssSelector("td:nth-child(3)")).getText();

                //ey = driver.executeScript("return jQuery(arguments[0]).find(\"td:nth-child(2)\").text();", row).toString();
                //value = driver.executeScript("return jQuery(arguments[0]).find(\"td:nth-child(3)\").text();", row).toString();

                key = driver.executeScript("return arguments[0].querySelector(\"td:nth-child(2)\").innerText;", row).toString();
                value = driver.executeScript("return arguments[0].querySelector(\"td:nth-child(3)\").innerText;", row).toString();
            } else {
                // Grab label and information within row to create a key value pair
                //key = row.findElement(By.cssSelector("td:nth-child(1)")).getText();
                //value = row.findElement(By.cssSelector("td:nth-child(2)")).getText();

                //key = driver.executeScript("return jQuery(arguments[0]).find(\"td:nth-child(1)\").text();", row).toString();
                //value = driver.executeScript("return jQuery(arguments[0]).find(\"td:nth-child(2)\").text();", row).toString();

                key = driver.executeScript("return arguments[0].querySelector(\"td:nth-child(1)\").innerText;", row).toString();
                value = driver.executeScript("return arguments[0].querySelector(\"td:nth-child(2)\").innerText;", row).toString();


            }

            // Call helper function to clean up the key
            key = _getKey(key);

            // If a key was found then add it to the info hash map
            if (!"".equals(key)) {
                info.put(key, value);
            }
        }

        return info;
    }

    /**
     * This function handles parsing out the data from the case circumstances page
     *
     * @param driver WebDriver to use
     * @return HashMap of circumstance information
     */
    private static HashMap<String, String> _parseCircumstances(ChromeDriver driver) {
        //driver.executeScript("change_block(\"circumstances\")");
        //_sleep(1200);

        return _utilParseTableRows(driver, By.cssSelector("#circumstances table tr"));
    }

    /**
     * This function handles parsing out the data from the case physical characteristics page
     *
     * @param driver WebDriver to use
     * @return HashMap of circumstance information
     */
    private static HashMap<String, String> _parsePhysicalCharacteristics(ChromeDriver driver) {
        //driver.executeScript("change_block(\"physical_characteristics\")");
        //_sleep(1200);

        return _utilParseTableRows(driver, By.cssSelector("#physical_characteristics table tr"));
    }

    /**
     * This function handles parsing out the data from the photos page
     *
     * @param driver WebDriver to use
     * @return HashMap of circumstance information
     */
    private static HashMap<String, String> _parsePhotos(ChromeDriver driver) {
        //driver.executeScript("change_block(\"photos\")");
        //_sleep(1200);

        // Info to return
        HashMap<String, String> info = new HashMap<String, String>();

        // Find all the images listed
        List<WebElement> images = driver.findElements(By.cssSelector("#photo_box img"));
        Iterator<WebElement> imagesIterator = images.iterator();

        // Loop over each image
        int count = 0;
        while (imagesIterator.hasNext()) {
            WebElement element = imagesIterator.next();

            // For each image get the src and add to return data
            String key = String.valueOf(count);
            String value = element.getAttribute("src");
            count += 1;

            info.put(key, value);
        }

        return info;
    }

    /**
     * This function handles parsing out the data from the case contacts page
     *
     * @param driver WebDriver to use
     * @param results HashMap to data to
     * @return HashMap of circumstance information
     */
    private static void _parseContacts(ChromeDriver driver, HashMap results) {
        //driver.executeScript("change_block(\"contacts\")");
        //_sleep(1200);

        // Since contacts has two important bits of information add them to the hash map
        results.put("caseManager", _utilParseTableRows(driver, By.cssSelector("#contacts .column2-unit-left table tr")));
        results.put("regionalAdministrator", _utilParseTableRows(driver, By.cssSelector("#contacts .column2-unit-right table tr")));
    }

    /**
     * This function handles parsing out the data from the case investigating agency page
     *
     * @param driver WebDriver to use
     * @return HashMap of circumstance information
     */
    private static HashMap<String, String> _parseInvestigatingAgency(ChromeDriver driver) {
        //driver.executeScript("change_block(\"police_information\")");
        //_sleep(1200);

        return _utilParseTableRows(driver, By.cssSelector("#police_information table tr"));
    }

    /**
     * This function makes the java process wait the given time
     *
     * @param time Amount of time to sleep in seconds
     */
    private static void _sleep(int time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * This function is a little helper function to transform the key that is given into something a bit more friendly
     * for json. Called from the parse table function
     *
     * @param key The key to look at to see if it can be transformed
     * @return
     */
    private static String _getKey(String key) {
        String returnKey = key.toLowerCase();

        switch (returnKey) {
            case "first name":
                returnKey = "firstName";
                break;
            case "last name":
                returnKey = "lastName";
                break;
            case "weight (pounds)":
                returnKey = "weight";
                break;
            case "age now":
                returnKey = "ageNow";
                break;
            case "date last seen":
                returnKey = "lastSeen";
                break;
            case "nickname/alias":
                returnKey = "nickname";
                break;
            case "height (inches)":
                returnKey = "height";
                break;
            case "date entered":
                returnKey = "dateEntered";
                break;
            case "age last seen":
                returnKey = "ageLastSeen";
                break;
            case "middle name":
                returnKey = "middleName";
                break;
            case "case number":
                returnKey = "caseNumber";
                break;
            case "zip code":
                returnKey = "zip";
                break;
            case "date reported":
                returnKey = "dateReported";
                break;
            case "address 1":
            case "address 2":
                returnKey = returnKey.replaceAll(" ", "");
                break;
            // Characteristics...
            case "left eye color":
                returnKey = "leftEyeColor";
                break;
            case "scars and marks":
                returnKey = "scarsAndMarks";
                break;
            case "finger and toe nails":
                returnKey = "fingersAndToeNails";
                break;
            case "foreign objects":
                returnKey = "foreignObjects";
                break;
            case "hair color":
                returnKey = "hairColor";
                break;
            case "eye description":
                returnKey = "eyeDescription";
                break;
            case "skeletal information":
                returnKey = "skeletalInformation";
                break;
            case "body hair":
                returnKey = "bodyHair";
                break;
            case "right eye color":
                returnKey = "rightEyeColor";
                break;
            case "facial hair":
                returnKey = "facialHair";
                break;
            case "other distinctive\nphysical characteristics":
                returnKey = "otherCharacteristics";
                break;
            case "head hair":
                returnKey = "headHair";
                break;
            case "artificial body parts\nand aids":
                returnKey = "prosthetics";
                break;
        }

        return returnKey;
    }
}
