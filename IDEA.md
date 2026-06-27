# Prayer Link
## Overview
Prayer link is a website that bridges the link between intercessors and people needing prayer. Users can submit a prayer, that prayer is sent to intercessors, but uniquely the person who prayed can receive updates when they’ve been prayed for, and give intercessors updates on answered prayer.

## User Flows
### New User, Unspecified Recipients
Website displays a button to start a prayer request. The user is asked if they want to submit to a specific prayer group, which is accessed via a passcode or QR scan. If not, the request is submitted to the service and the user is invited to close or submit another prayer request. The request is forwarded to a random group of intercessors.

### New User, Specific Recipients
Website displays a button to start a prayer request. The user is asked if they want to submit to a specific prayer group, which is accessed via a passcode or QR scan. After scanning/inputting, the flow proceeds as above, but with a specific group of intercessors.

### Returning User
A returning user sees their previous prayer requests ‘floating’ around the central button to submit a prayer request. Prayers can have ‘updates’ when prayed for, displayed as a prayer emoji badge with a numeral representing the people who have prayed. If the user clicks one of these prayers, they should have the option to respond with an update. This update gets forwarded to the intercercessors. This should close the prayer, and the user should be warned this.

### Intercessor Flow
Ideally, intercessors are messaged through an app like WhatsApp. The message should include the prayer and a button to indicate they have prayed for the request. This impacts the badges as specified above.

### Admin Flow
Admin access should be hidden for all intents and purposes from the normal user flow. Application Admin access allows to configure registered prayer groups and set passcodes/QRs for that, along with editing those groups and the contacts within them. It should also provide a dashboard of prayers. Prayer Group Admin allows a subset of these features, solely editing the group contacts.

## Project Design
The project should be made up of a frontend and multiple backend microservices. Modern technologies should be applied in all areas. The frontend should never ask for a login, instead device fingerprinting used to identify returning users. All should be deployable in AWS and conform to 12 factor apps.

### UI Design
The design should be light, airy and make use of HTML animations. A site like https://kynejang.com is a good example - colourful animated backgrounds that are minimal and evoke an ethereal setting. The website should function seamlessly on mobile or desktop. The UI should be minimal and respectful.

### Backend Design
The backend microservices should be Java Spring Boot apps, backed by maven as a build service. Direct or Queue Based Messaging is acceptable. As mentioned prior, users should be fingerprinted so no accounts. Metrics should be published for error and totals, accessible via prometheus instance with grafana querying. All apps should be secure.


