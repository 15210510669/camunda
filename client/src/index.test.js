const fs = require('fs');

const blacklist = ['setupTests.js'];

function getAllFilesInDirectory(dir, filelist) {
  var files = fs.readdirSync(dir);
  filelist = filelist || [];
  files.forEach(function(file) {
    if (fs.statSync(dir + file).isDirectory()) {
      filelist = getAllFilesInDirectory(dir + file + '/', filelist);
    }
    else {
      filelist.push(dir + file);
    }
  });
  return filelist;
};

function getInternalModules(dir) {
  return new Set(fs.readdirSync(dir).map(entry => entry.split('.')[0]));
}

function isJavascriptFile(filename) {
  return (/^(?!.*test\.js).*\.js$/g).test(filename);
}

function isFileNotBlacklisted(filename) {
  return !blacklist.some(blacklistEntry => filename.includes(blacklistEntry));
}

function getImportedModules(content) {
  const regex = RegExp('import\\s[^"\'`]*["\'`]([^.][^"\'`\\s]*)', 'g');

  const matches = [];
  let result = regex.exec(content);

  while(result !== null) {
    if(result[1]) {
      matches.push(result[1].split('/')[0]);
    }
    result = regex.exec(content);
  }

  return matches;
}

function getDeclaredDependencies() {
  return Object.keys(
    JSON.parse(fs.readFileSync(__dirname + '/../package.json', 'utf8')).dependencies
  );
}

const allFiles = getAllFilesInDirectory(__dirname + '/');
const filesToCheck = allFiles
  .filter(isJavascriptFile)
  .filter(isFileNotBlacklisted);
const usedModules = new Set();

filesToCheck.forEach(filename => {
  const fileContent = fs.readFileSync(filename, 'utf8');
  const imports = getImportedModules(fileContent);

  imports.forEach(entry => usedModules.add(entry));
});

getInternalModules(__dirname + '/modules/').forEach(internalModule => {
  usedModules.delete(internalModule);
});

const declaredDependencies = getDeclaredDependencies();

it('should use all declared dependencies in production code', () => {
  const unusedDependencies = new Set(declaredDependencies);
  usedModules.forEach(entry => unusedDependencies.delete(entry));

  expect([...unusedDependencies]).toHaveLength(0);
});

it('should declare all used dependencies', () => {
  const undeclaredDependencies = new Set();
  usedModules.forEach(entry => {
    if(!declaredDependencies.includes(entry)) {
      undeclaredDependencies.add(entry);
    }
  });

  expect([...undeclaredDependencies]).toHaveLength(0);
});
