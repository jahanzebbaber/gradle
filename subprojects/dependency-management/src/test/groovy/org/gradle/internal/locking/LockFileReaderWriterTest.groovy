/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.locking

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class LockFileReaderWriterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    TestFile lockDir = tmpDir.createDir(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER)

    @Subject
    LockFileReaderWriter lockFileReaderWriter
    FileResolver resolver = Mock()
    DomainObjectContext context = Mock()
    RegularFileProperty lockFile = TestFiles.filePropertyFactory().newFileProperty()

    def setup() {
        context.identityPath(_) >> { String value -> Path.path(value) }
        resolver.canResolveRelativePath() >> true
        resolver.resolve(LockFileReaderWriter.DEPENDENCY_LOCKING_FOLDER) >> lockDir
        resolver.resolve(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME) >> tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME)
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)
    }

    def 'writes a unique lock file'() {
        when:
        lockDir.deleteDir()
        lockFileReaderWriter.writeUniqueLockfile([a: ['foo', 'bar'], b: ['foo'], c: []])

        then:
        tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME).text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
bar=a
foo=a,b
empty=c
""".denormalize()
        !lockDir.exists()
    }

    def 'writes dependencies and configurations sorted in the unique lock file'() {
        when:
        lockFileReaderWriter.writeUniqueLockfile([b: ['foo', 'bar'], d: ['bar', 'foobar'],a: ['foo'], e: [], f: [], c: []])

        then:
        tmpDir.file(LockFileReaderWriter.UNIQUE_LOCKFILE_NAME).text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
bar=b,d
foo=a,b
foobar=d
empty=c,e,f
""".denormalize()
    }

    def 'writes a unique lock file to a custom location'() {
        def lockFile = tmpDir.file('different', 'lock.file')
        lockDir.deleteDir()
        given:
        this.lockFile.set(lockFile)

        when:
        lockFileReaderWriter.writeUniqueLockfile([a: ['foo', 'bar'], b: ['foo'], c: []])

        then:
        lockFile.text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
bar=a
foo=a,b
empty=c
""".denormalize()
        !lockDir.exists()
    }

    def 'writes a legacy lock file on persist'() {
        when:
        lockFileReaderWriter.writeLockFile('conf', ['line1', 'line2'])

        then:
        lockDir.file('conf.lockfile').text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
line1
line2
""".denormalize()
    }

    def 'reads a legacy lock file'() {
        given:
        lockDir.file('conf.lockfile') << """#Ignored
line1

line2"""

        when:
        def result = lockFileReaderWriter.readLockFile('conf')

        then:
        result == ['line1', 'line2']
    }

    def 'reads a unique lock file'() {
        given:
        tmpDir.file('gradle.lockfile') << """#ignored
bar=a,c
foo=a,b,c
empty=d
"""

        when:
        def result = lockFileReaderWriter.readUniqueLockFile()

        then:
        result == [a: ['bar', 'foo'], b: ['foo'], c: ['bar', 'foo'], d: []]
    }

    def 'reads a unique lock file from a custom location'() {
        given:
        def file = tmpDir.file('custom', 'lock.file')
        lockFile.set(file)
        file << """#ignored
bar=a,c
foo=a,b,c
empty=d
"""

        when:
        def result = lockFileReaderWriter.readUniqueLockFile()

        then:
        result == [a: ['bar', 'foo'], b: ['foo'], c: ['bar', 'foo'], d: []]
    }

    def 'writes a unique lock file with prefix'() {
        when:
        context.isScript() >> true
        resolver.resolve("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME") >> tmpDir.file("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME")
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)
        lockFileReaderWriter.writeUniqueLockfile([a: ['foo', 'bar'], b: ['foo'], c: []])

        then:
        tmpDir.file("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME").text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
bar=a
foo=a,b
empty=c
""".denormalize()

    }

    def 'writes a legacy lock file with prefix on persist'() {
        when:
        context.isScript() >> true
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)
        lockFileReaderWriter.writeLockFile('conf', ['line1', 'line2'])

        then:
        lockDir.file('buildscript-conf.lockfile').text == """${LockFileReaderWriter.LOCKFILE_HEADER_LIST.join('\n')}
line1
line2
""".denormalize()
    }

    def 'reads a legacy lock file with prefix'() {
        given:
        context.isScript() >> true
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)
        lockDir.file('buildscript-conf.lockfile') << """#Ignored
line1

line2"""

        when:
        def result = lockFileReaderWriter.readLockFile('conf')

        then:
        result == ['line1', 'line2']
    }

    def 'reads a unique lock file with prefix'() {
        given:
        context.isScript() >> true
        resolver.resolve("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME") >> tmpDir.file("buildscript-$LockFileReaderWriter.UNIQUE_LOCKFILE_NAME")
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)
        tmpDir.file('buildscript-gradle.lockfile') << """#ignored
bar=a,c
foo=a,b,c
empty=d
"""

        when:
        def result = lockFileReaderWriter.readUniqueLockFile()

        then:
        result == [a: ['bar', 'foo'], b: ['foo'], c: ['bar', 'foo'], d: []]
    }

    def 'fails to read a unique lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)

        when:
        lockFileReaderWriter.readUniqueLockFile()

        then:
        def ex = thrown(IllegalStateException)
        1 * context.getProjectPath() >> Path.path('foo')
        ex.getMessage().contains('Dependency locking cannot be used for project')
        ex.getMessage().contains('foo')
    }

    def 'fails to read a legacy lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)

        when:
        lockFileReaderWriter.readLockFile('foo')

        then:
        def ex = thrown(IllegalStateException)
        1 * context.identityPath('foo') >> Path.path('foo')
        ex.getMessage().contains('Dependency locking cannot be used for configuration')
        ex.getMessage().contains('foo')
    }

    def 'fails to write a unique lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)

        when:
        lockFileReaderWriter.writeUniqueLockfile([foo: ['a:b:1.0']])

        then:
        def ex = thrown(IllegalStateException)
        1 * context.getProjectPath() >> Path.path('foo')
        ex.getMessage().contains('Dependency locking cannot be used for project')
        ex.getMessage().contains('foo')
    }

    def 'fails to write a legacy lockfile if root could not be determined'() {
        FileResolver resolver = Mock()
        resolver.canResolveRelativePath() >> false
        lockFileReaderWriter = new LockFileReaderWriter(resolver, context, lockFile)

        when:
        lockFileReaderWriter.writeLockFile('foo', [])

        then:
        def ex = thrown(IllegalStateException)
        1 * context.identityPath('foo') >> Path.path('foo')
        ex.getMessage().contains('Dependency locking cannot be used for configuration')
        ex.getMessage().contains('foo')
    }
}
