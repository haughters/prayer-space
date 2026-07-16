import xml.etree.ElementTree as ET

def update_pom():
    tree = ET.parse('pom.xml')
    root = tree.getroot()
    ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}
    ET.register_namespace('', ns['mvn'])
    
    plugins = root.findall('.//mvn:plugin', ns)
    for plugin in plugins:
        artifactId = plugin.find('mvn:artifactId', ns)
        if artifactId is not None and artifactId.text == 'native-maven-plugin':
            configuration = plugin.find('mvn:configuration', ns)
            buildArgs = configuration.find('mvn:buildArgs', ns)
                
            arg = ET.SubElement(buildArgs, 'buildArg')
            arg.text = '--initialize-at-build-time=com.prayerlink.admin.repository,com.prayerlink.group.repository,com.prayerlink.identity.repository,com.prayerlink.prayer.repository'
            break
            
    tree.write('pom.xml', xml_declaration=True, encoding='UTF-8')

update_pom()
