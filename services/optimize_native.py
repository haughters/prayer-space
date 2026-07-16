import xml.etree.ElementTree as ET
import glob
import os

os.chdir('services')

# 1. Update native-maven-plugin in parent pom.xml
tree = ET.parse('pom.xml')
root = tree.getroot()
ns = {'mvn': 'http://maven.apache.org/POM/4.0.0'}
ET.register_namespace('', ns['mvn'])

plugins = root.findall('.//mvn:plugin', ns)
for plugin in plugins:
    artifactId = plugin.find('mvn:artifactId', ns)
    if artifactId is not None and artifactId.text == 'native-maven-plugin':
        configuration = plugin.find('mvn:configuration', ns)
        if configuration is None:
            configuration = ET.SubElement(plugin, 'configuration')
            
        buildArgs = configuration.find('mvn:buildArgs', ns)
        if buildArgs is None:
            buildArgs = ET.SubElement(configuration, 'buildArgs')
        
        args = ["-Os", "-H:-InstallExitHandlers"]
        for a in args:
            arg = ET.SubElement(buildArgs, 'buildArg')
            arg.text = a

tree.write('pom.xml', xml_declaration=True, encoding='UTF-8')

# 2. Exclude heavy HTTP clients in child POMs
for pom_file in glob.glob('*/pom.xml'):
    tree = ET.parse(pom_file)
    root = tree.getroot()
    ET.register_namespace('', ns['mvn'])
    
    dependencies = root.findall('.//mvn:dependency', ns)
    modified = False
    has_aws = False
    
    for dep in dependencies:
        groupId = dep.find('mvn:groupId', ns)
        if groupId is not None and groupId.text == 'software.amazon.awssdk':
            has_aws = True
            artifactId = dep.find('mvn:artifactId', ns).text
            if artifactId in ['dynamodb', 'dynamodb-enhanced', 'eventbridge', 'sqs']:
                exclusions = dep.find('mvn:exclusions', ns)
                if exclusions is None:
                    exclusions = ET.SubElement(dep, 'exclusions')
                
                # Exclude apache-client
                ex1 = ET.SubElement(exclusions, 'exclusion')
                ET.SubElement(ex1, 'groupId').text = 'software.amazon.awssdk'
                ET.SubElement(ex1, 'artifactId').text = 'apache-client'
                
                # Exclude netty-nio-client
                ex2 = ET.SubElement(exclusions, 'exclusion')
                ET.SubElement(ex2, 'groupId').text = 'software.amazon.awssdk'
                ET.SubElement(ex2, 'artifactId').text = 'netty-nio-client'
                
                modified = True
                
    if has_aws:
        deps = root.find('.//mvn:dependencies', ns)
        if deps is not None:
            new_dep = ET.SubElement(deps, 'dependency')
            ET.SubElement(new_dep, 'groupId').text = 'software.amazon.awssdk'
            ET.SubElement(new_dep, 'artifactId').text = 'url-connection-client'
            modified = True
            
    if modified:
        tree.write(pom_file, xml_declaration=True, encoding='UTF-8')

