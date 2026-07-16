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
            for arg in buildArgs.findall('mvn:buildArg', ns):
                if '--initialize-at-build-time' in arg.text:
                    arg.text = arg.text + ',software.amazon.awssdk.enhanced.dynamodb.mapper,software.amazon.awssdk.enhanced.dynamodb.TableSchema'
            break
            
    tree.write('pom.xml', xml_declaration=True, encoding='UTF-8')

update_pom()
