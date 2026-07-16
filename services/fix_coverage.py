import os
import glob

files = glob.glob('*/src/test/java/com/prayerlink/*/*CoverageTest.java')

for f in files:
    with open(f, 'r') as file:
        content = file.read()
        
    if 'aws.accessKeyId' in content:
        continue
        
    target = 'void testApplicationMain() {\n        try {\n'
    replacement = '''void testApplicationMain() {
        System.setProperty("aws.accessKeyId", "dummy");
        System.setProperty("aws.secretAccessKey", "dummy");
        System.setProperty("aws.region", "eu-west-1");
        try {
'''
    content = content.replace(target, replacement)
    
    target2 = '} catch (Throwable e) {\n'
    replacement2 = '''} catch (Throwable e) {
        } finally {
            System.clearProperty("aws.accessKeyId");
            System.clearProperty("aws.secretAccessKey");
            System.clearProperty("aws.region");
        }
        try {
            if(false) throw new RuntimeException();
        } catch (Throwable e2) {
'''
    # Careful, we only want to replace the first catch block in testApplicationMain, 
    # but the structure is very specific. 
    # Let's just do a simpler replacement on the main() invocation block.
